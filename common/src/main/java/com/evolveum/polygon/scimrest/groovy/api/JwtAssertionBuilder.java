/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and signs a JWT assertion for use as an OAuth 2.0 Bearer token request (RFC 7523).
 *
 * <p>Default header: {@code {"alg":"RS256","typ":"JWT"}}. All claims must be set explicitly via
 * {@link #claim(String, Object)}; the caller is responsible for {@code iss}, {@code sub},
 * {@code aud}, {@code iat}, {@code exp}, and {@code jti}.
 */
public class JwtAssertionBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> headers = new LinkedHashMap<>();
    private final Map<String, Object> claims  = new LinkedHashMap<>();

    public JwtAssertionBuilder() {
        headers.put("alg", "RS256");
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

    public String sign(GuardedString privateKey, String signingAlgorithm, String keyAlgorithm) {
        var accessor = new GuardedStringAccessor();
        privateKey.access(accessor);
        return sign(accessor.getClearString(), signingAlgorithm, keyAlgorithm);
    }

    public String sign(String pemKey, String signingAlgorithm, String keyAlgorithm) {
        String header = base64url(toJson(headers));
        String payload = base64url(toJson(claims));
        String signingInput = header + "." + payload;

        byte[] signature = doSign(signingInput, parsePrivateKey(pemKey, keyAlgorithm), signingAlgorithm);
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ConnectorException("Failed to serialize JWT: " + e.getMessage(), e);
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

    private static byte[] doSign(String input, PrivateKey key, String algorithm) {
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(key);
            sig.update(input.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (Exception e) {
            throw new ConnectorIOException("Failed to sign JWT assertion: " + e.getMessage(), e);
        }
    }

    private static String base64url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
