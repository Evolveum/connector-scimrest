/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import java.nio.charset.StandardCharsets;

/**
 * URI percent-encoding helpers shared by request building ({@link JdkHttpRequestConverter}) and
 * AWS SigV4 canonicalization ({@link AwsRequestSigner}).
 *
 * <p>Keeping both producers on the same encoder guarantees that the path put on the wire and the
 * path fed into the SigV4 canonical request stay consistent — a mismatch between the two silently
 * breaks the signature.
 */
final class UriEncoding {

    private UriEncoding() {}

    /** Characters that AWS SigV4 leaves unencoded: RFC 3986 unreserved ({@code A-Za-z0-9-._~}). */
    private static final boolean[] AWS_UNRESERVED = new boolean[128];

    /** Characters allowed unencoded inside an RFC 3986 path segment ({@code pchar}). */
    private static final boolean[] PATH_PCHAR = new boolean[128];

    static {
        for (int c = 'A'; c <= 'Z'; c++) { AWS_UNRESERVED[c] = true; PATH_PCHAR[c] = true; }
        for (int c = 'a'; c <= 'z'; c++) { AWS_UNRESERVED[c] = true; PATH_PCHAR[c] = true; }
        for (int c = '0'; c <= '9'; c++) { AWS_UNRESERVED[c] = true; PATH_PCHAR[c] = true; }
        for (char c : new char[]{'-', '.', '_', '~'}) { AWS_UNRESERVED[c] = true; PATH_PCHAR[c] = true; }
        // pchar = unreserved / sub-delims / ":" / "@" (RFC 3986 §3.3)
        for (char c : "!$&'()*+,;=:@".toCharArray()) PATH_PCHAR[c] = true;
    }

    /**
     * AWS SigV4 canonical encoding of a path: only unreserved characters survive, {@code /}
     * separators are preserved. Applying this twice yields the double-encoded form required for
     * every service except S3.
     */
    static String awsEncodePath(String path) {
        return encodeEachSegment(path, AWS_UNRESERVED, false);
    }

    /** AWS SigV4 canonical encoding of a single value (e.g. a query key/value); {@code /} is encoded too. */
    static String awsEncode(String value) {
        var out = new StringBuilder();
        encodeSegment(value, AWS_UNRESERVED, false, out);
        return out.toString();
    }

    /**
     * RFC 3986 path encoding for the wire request: legal {@code pchar} (including {@code :} and
     * {@code @}) and existing {@code %} escapes are kept; only genuinely illegal characters
     * (spaces, control characters, non-ASCII, delimiters) are percent-encoded. This leaves already
     * valid paths untouched while making paths with special characters well-formed.
     */
    static String encodePath(String path) {
        return encodeEachSegment(path, PATH_PCHAR, true);
    }

    private static String encodeEachSegment(String path, boolean[] allowed, boolean keepPercent) {
        String[] segments = path.split("/", -1);
        var out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) out.append('/');
            encodeSegment(segments[i], allowed, keepPercent, out);
        }
        return out.toString();
    }

    private static void encodeSegment(String segment, boolean[] allowed, boolean keepPercent, StringBuilder out) {
        byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c < 128 && allowed[c]) {
                out.append((char) c);
            } else if (keepPercent && c == '%') {
                out.append('%');
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
    }
}
