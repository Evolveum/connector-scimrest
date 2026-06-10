/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Signs HTTP requests using AWS Signature Version 4 (SigV4).
 *
 * <p>Used as a static utility for the builtin customizer, or as an instance inside the
 * {@code awsSignature { beforeSign { ... } }} Groovy hook to include extra signed headers.
 *
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html">AWS SigV4</a>
 */
public class AwsRequestSigner {

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String ALGORITHM  = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";

    // -------------------------------------------------------------------------
    // Instance state — used when constructed for the beforeSign hook
    // -------------------------------------------------------------------------

    private final HttpRequestSpecification request;
    private final List<String> extraSignedHeaders = new ArrayList<>();
    private final String region;
    private final String service;
    private final String accessKey;
    private final GuardedString secretKey;
    private final GuardedString sessionToken;

    /** Test-only override for the signing timestamp; {@code null} means "now". */
    private ZonedDateTime overrideTimestamp;

    public AwsRequestSigner(HttpRequestSpecification request, String region, String service,
            String accessKey, GuardedString secretKey, GuardedString sessionToken) {
        this.request      = request;
        this.region       = region;
        this.service      = service;
        this.accessKey    = accessKey;
        this.secretKey    = secretKey;
        this.sessionToken = sessionToken;
    }

    public AwsRequestSigner signHeader(String name) {
        extraSignedHeaders.add(name.toLowerCase());
        return this;
    }

    /** Pins the signing timestamp; used by tests to reproduce published SigV4 vectors. */
    AwsRequestSigner withTimestamp(ZonedDateTime timestamp) {
        this.overrideTimestamp = timestamp;
        return this;
    }

    /** Computes the SigV4 signature, sets all required {@code x-amz-*} headers, and returns the {@code Authorization} header value. */
    public String sign() {
        return doSign(request, accessKey, secretKey, sessionToken, region, service, extraSignedHeaders,
                overrideTimestamp != null ? overrideTimestamp : ZonedDateTime.now(ZoneOffset.UTC));
    }

    // -------------------------------------------------------------------------
    // Static API — used by the builtin customizer
    // -------------------------------------------------------------------------

    public static void sign(HttpRequestSpecification request,
            String accessKey, GuardedString secretKey,
            GuardedString sessionToken,
            String region, String service) {
        request.header("Authorization",
                doSign(request, accessKey, secretKey, sessionToken, region, service, List.of(),
                        ZonedDateTime.now(ZoneOffset.UTC)));
    }

    // -------------------------------------------------------------------------
    // Core signing logic
    // -------------------------------------------------------------------------

    private static String doSign(HttpRequestSpecification request,
            String accessKey, GuardedString secretKey,
            GuardedString sessionToken,
            String region, String service,
            List<String> extraSignedHeaders,
            ZonedDateTime now) {

        var secretAccessor = new GuardedStringAccessor();
        secretKey.access(secretAccessor);
        String secret = secretAccessor.getClearString();

        String sessionTokenValue = null;
        if (sessionToken != null) {
            var tokenAccessor = new GuardedStringAccessor();
            sessionToken.access(tokenAccessor);
            sessionTokenValue = tokenAccessor.getClearString();
        }

        String dateStr     = now.format(DATE_FMT);
        String datetimeStr = now.format(DATETIME_FMT);

        String presetBodyHash = request.getHeaders().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("x-amz-content-sha256") && !e.getValue().isEmpty())
                .map(e -> e.getValue().get(0))
                .findFirst().orElse(null);
        String bodyHash = presetBodyHash != null ? presetBodyHash
                : sha256Hex(request.getBody() != null ? request.getBody() : new byte[0]);

        // Add required amz headers BEFORE computing canonical headers.
        // header() appends, so only set x-amz-content-sha256 when it wasn't already supplied.
        if (presetBodyHash == null) {
            request.header("x-amz-content-sha256", bodyHash);
        }
        request.header("x-amz-date", datetimeStr);
        if (sessionTokenValue != null && !sessionTokenValue.isBlank()) {
            request.header("x-amz-security-token", sessionTokenValue);
        }

        String host = extractHost(request.getBaseUri());

        // Mandatory signed headers (alphabetical order guaranteed by TreeMap)
        var allHeaders = new TreeMap<String, String>();
        allHeaders.put("host", host);
        allHeaders.put("x-amz-content-sha256", bodyHash);
        allHeaders.put("x-amz-date", datetimeStr);
        if (sessionTokenValue != null && !sessionTokenValue.isBlank()) {
            allHeaders.put("x-amz-security-token", sessionTokenValue);
        }

        // Extra headers from beforeSign hook — look up values from the request
        for (String extraName : extraSignedHeaders) {
            request.getHeaders().forEach((name, values) -> {
                if (name.equalsIgnoreCase(extraName) && !values.isEmpty()) {
                    allHeaders.putIfAbsent(extraName, values.get(0).trim());
                }
            });
        }

        String signedHeaders    = String.join(";", allHeaders.keySet());
        String canonicalHeaders = buildCanonicalHeaders(allHeaders);

        String method         = request.getHttpMethod().name();
        String canonicalUri   = buildCanonicalUri(request, service);
        String canonicalQuery = buildCanonicalQueryString(request.getQueryParameters());

        String canonicalRequest = String.join("\n",
                method, canonicalUri, canonicalQuery,
                canonicalHeaders, signedHeaders, bodyHash);

        String credentialScope = dateStr + "/" + region + "/" + service + "/" + TERMINATOR;
        String stringToSign = String.join("\n",
                ALGORITHM, datetimeStr, credentialScope,
                sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = deriveSigningKey(secret, dateStr, region, service);
        String signature  = hmacSha256Hex(signingKey, stringToSign);

        return ALGORITHM
                + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static String buildCanonicalHeaders(TreeMap<String, String> headers) {
        var sb = new StringBuilder();
        headers.forEach((name, value) -> sb.append(name).append(':').append(normalizeHeaderValue(value)).append('\n'));
        return sb.toString();
    }

    /** SigV4 requires header values trimmed and any run of internal whitespace collapsed to a single space. */
    private static String normalizeHeaderValue(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String extractHost(String baseUri) {
        try {
            URI uri = new URI(baseUri);
            int port = uri.getPort();
            String scheme = uri.getScheme();
            boolean isDefaultPort = (port == -1)
                    || ("https".equalsIgnoreCase(scheme) && port == 443)
                    || ("http".equalsIgnoreCase(scheme) && port == 80);
            return isDefaultPort ? uri.getHost() : uri.getHost() + ":" + port;
        } catch (Exception e) {
            throw new ConnectorException("Cannot parse base URI for AWS signing: " + baseUri, e);
        }
    }

    private static String buildCanonicalUri(HttpRequestSpecification request, String service) {
        String endpoint = request.getApiEndpoint();
        if (endpoint != null) {
            for (var entry : request.getPathParameters().entrySet()) {
                String val = entry.getValue() instanceof Attribute a
                        ? AttributeUtil.getAsStringValue(a)
                        : String.valueOf(entry.getValue());
                endpoint = endpoint.replace("{" + entry.getKey() + "}", val != null ? val : "");
            }
        }

        String basePath = extractPath(request.getBaseUri());
        StringBuilder path = new StringBuilder(basePath.isEmpty() ? "" : basePath);
        if (endpoint != null && !endpoint.isEmpty()) {
            if (path.isEmpty() || path.charAt(path.length() - 1) != '/') path.append('/');
            path.append(endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);
        }
        String sub = request.getSubpath();
        if (!sub.isEmpty()) {
            if (path.isEmpty() || path.charAt(path.length() - 1) != '/') path.append('/');
            path.append(sub.startsWith("/") ? sub.substring(1) : sub);
        }
        String rawPath = path.isEmpty() ? "/" : path.toString();

        // SigV4 canonical URI: single URI-encoding for S3, double encoding for every other service
        // (RFC 3986 §5 / AWS SigV4 spec). The server re-encodes the already-encoded wire path, so the
        // double form is what it computes for non-S3 services.
        String encoded = UriEncoding.awsEncodePath(rawPath);
        if (!"s3".equalsIgnoreCase(service)) {
            encoded = UriEncoding.awsEncodePath(encoded);
        }
        return encoded;
    }

    private static String extractPath(String baseUri) {
        try {
            String path = new URI(baseUri).getPath();
            return path == null ? "" : path;
        } catch (Exception e) {
            return "";
        }
    }

    private static String buildCanonicalQueryString(Map<String, String> params) {
        if (params.isEmpty()) return "";
        return params.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey()
                        .thenComparing(Map.Entry.comparingByValue()))
                .map(e -> UriEncoding.awsEncode(e.getKey()) + "=" + UriEncoding.awsEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private static byte[] deriveSigningKey(String secret, String date, String region, String service) {
        byte[] kSecret  = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        byte[] kDate    = hmacSha256(kSecret, date);
        byte[] kRegion  = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, TERMINATOR);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(data));
        } catch (Exception e) {
            throw new ConnectorException("SHA-256 failed", e);
        }
    }

    private static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConnectorException("HMAC-SHA256 failed", e);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return hex(hmacSha256(key, data));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
