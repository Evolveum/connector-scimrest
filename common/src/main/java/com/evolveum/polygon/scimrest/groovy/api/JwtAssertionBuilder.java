/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and signs a JWT assertion.
 *
 * <p>Supported algorithms: HS256, HS384, HS512, RS256, RS384, RS512,
 * PS256, PS384, PS512, ES256, ES384, ES512.
 *
 * <p>Use {@link #sign(String, byte[])} for all algorithms — pass raw secret bytes for HS*,
 * or UTF-8-encoded PEM private key bytes for asymmetric algorithms.
 *
 * <p>Claims can be set individually via {@link #claim(String, Object)} or in bulk from a JSON
 * string via {@link #claimsFromJson(String)}.
 */
public class JwtAssertionBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> headers = new LinkedHashMap<>();
    private final Map<String, Object> claims  = new LinkedHashMap<>();

    public JwtAssertionBuilder() {
        headers.put("typ", "JWT");
    }

    public JwtAssertionBuilder header(String name, Object value) {
        headers.put(name, value);
        return this;
    }

    public JwtAssertionBuilder claim(String name, Object value) {
        claims.put(name, value);
        return this;
    }

    /** Parses {@code json} and merges all top-level entries into the claims map. */
    public JwtAssertionBuilder claimsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return this;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {});
            claims.putAll(parsed);
        } catch (JsonProcessingException e) {
            throw new ConnectorException("Failed to parse JWT payload JSON: " + e.getMessage(), e);
        }
        return this;
    }

    /**
     * Signs the JWT using a plain-text secret or PEM private key.
     *
     * <p>For HS* algorithms: uses {@code secret} as the HMAC key, optionally Base64-decoded first.
     * For RS*, PS*, ES* algorithms: treats {@code secret} as a PEM private key;
     * {@code secretBase64Encoded} is ignored.
     */
    public String sign(String jwtAlgorithm, String secret, Boolean secretBase64Encoded) {
        if (jwtAlgorithm == null) {
            throw new ConnectorException("JWT algorithm must be set");
        }
        byte[] keyBytes;
        if (jwtAlgorithm.toUpperCase().startsWith("HS")) {
            keyBytes = Boolean.TRUE.equals(secretBase64Encoded)
                    ? Base64.getDecoder().decode(secret)
                    : secret.getBytes(StandardCharsets.UTF_8);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return sign(jwtAlgorithm, keyBytes);
    }

    /**
     * Signs the JWT using the given JWT algorithm name and key material.
     *
     * <p>For HS* algorithms: {@code keyBytes} is the raw HMAC secret.
     * For RS*, PS*, ES* algorithms: {@code keyBytes} is the UTF-8-encoded PEM private key.
     */
    public String sign(String jwtAlgorithm, byte[] keyBytes) {
        headers.put("alg", jwtAlgorithm.toUpperCase());
        String header = base64url(toJson(headers));
        String payload = base64url(toJson(claims));
        String signingInput = header + "." + payload;
        byte[] signature = doSign(signingInput, jwtAlgorithm.toUpperCase(), keyBytes);
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    /** Convenience overload for asymmetric algorithms — accepts a {@link GuardedString} PEM private key. */
    public String sign(String jwtAlgorithm, GuardedString privateKey) {
        var accessor = new GuardedStringAccessor();
        privateKey.access(accessor);
        return sign(jwtAlgorithm, accessor.getClearString().getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Internal signing
    // -------------------------------------------------------------------------

    private static byte[] doSign(String input, String algorithmName, byte[] keyBytes) {
        var alg = Algorithm.from(algorithmName);
        try {
            if (alg.keyType == Algorithm.KeyType.HMAC) {
                Mac mac = Mac.getInstance(alg.jcaName);
                mac.init(new SecretKeySpec(keyBytes, alg.jcaName));
                return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            }
            String keyFactoryAlg = alg.keyType == Algorithm.KeyType.EC ? "EC" : "RSA";
            PrivateKey key = parsePrivateKey(new String(keyBytes, StandardCharsets.UTF_8), keyFactoryAlg);
            Signature sig = Signature.getInstance(alg.jcaName);
            if (alg.pssParams != null) {
                sig.setParameter(alg.pssParams);
            }
            sig.initSign(key);
            sig.update(input.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorIOException("JWT signing failed (" + algorithmName + "): " + e.getMessage(), e);
        }
    }

    private enum Algorithm {
        HS256("HmacSHA256",   KeyType.HMAC, null),
        HS384("HmacSHA384",   KeyType.HMAC, null),
        HS512("HmacSHA512",   KeyType.HMAC, null),
        RS256("SHA256withRSA", KeyType.RSA, null),
        RS384("SHA384withRSA", KeyType.RSA, null),
        RS512("SHA512withRSA", KeyType.RSA, null),
        PS256("RSASSA-PSS",   KeyType.RSA,  new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)),
        PS384("RSASSA-PSS",   KeyType.RSA,  new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1)),
        PS512("RSASSA-PSS",   KeyType.RSA,  new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1)),
        ES256("SHA256withECDSA", KeyType.EC, null),
        ES384("SHA384withECDSA", KeyType.EC, null),
        ES512("SHA512withECDSA", KeyType.EC, null);

        enum KeyType { HMAC, RSA, EC }

        final String jcaName;
        final KeyType keyType;
        final PSSParameterSpec pssParams;

        Algorithm(String jcaName, KeyType keyType, PSSParameterSpec pssParams) {
            this.jcaName = jcaName;
            this.keyType = keyType;
            this.pssParams = pssParams;
        }

        static Algorithm from(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConnectorException("Unsupported JWT algorithm: " + name);
            }
        }
    }

    private static PrivateKey parsePrivateKey(String pem, String keyAlgorithm) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance(keyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new ConnectorException("Failed to parse private key (expected PKCS#8 PEM): " + e.getMessage(), e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ConnectorException("Failed to serialize JWT: " + e.getMessage(), e);
        }
    }

    private static String base64url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
