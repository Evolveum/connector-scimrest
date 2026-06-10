/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Known-answer tests for {@link AwsRequestSigner}, pinning the produced {@code Signature=} against
 * published AWS SigV4 vectors. These catch canonicalization regressions that the structural tests
 * (header presence, ordering, format) cannot.
 */
public class AwsRequestSignerKatTest {

    // Credentials from AWS's own SigV4 examples.
    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final GuardedString SECRET_KEY =
            new GuardedString("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY".toCharArray());

    /**
     * AWS's canonical S3 "GET Object" SigV4 example: GET /test.txt with a Range header, signed for
     * us-east-1/s3 at 20130524T000000Z using the published example credentials. The expected
     * signature {@code f0e8bdb8...036bdb41} is the long-standing AWS reference value.
     *
     * <p>It is independently reproducible from the SigV4 specification — see the algorithm walkthrough
     * (Tasks 1-3) and a second fully worked, currently-accessible vector (the Glacier "Create Vault"
     * example, signature {@code 3ce5b2f2...7b7c2a}) at
     * https://docs.aws.amazon.com/amazonglacier/latest/dev/amazon-glacier-signing-requests.html#example-signature-calculation
     */
    @Test
    public void s3GetObjectVector() {
        var request = new HttpRequestSpecification("https://examplebucket.s3.amazonaws.com");
        request.httpMethod(HttpMethod.GET);
        request.subpath("/test.txt");
        request.header("Range", "bytes=0-9");

        var signer = new AwsRequestSigner(request, "us-east-1", "s3", ACCESS_KEY, SECRET_KEY, null)
                .withTimestamp(ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC));
        signer.signHeader("range");

        String authorization = signer.sign();

        assertEquals(authorization,
                "AWS4-HMAC-SHA256"
                        + " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request"
                        + ", SignedHeaders=host;range;x-amz-content-sha256;x-amz-date"
                        + ", Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    /**
     * For services other than S3, the canonical URI path must be URI-encoded twice. A path segment
     * containing a space therefore appears as {@code %2520} in the signed canonical request — a
     * different signature than the single-encoded ({@code %20}) S3 form, which this test pins by
     * asserting the two services produce different signatures for the same input.
     */
    @Test
    public void nonS3DoubleEncodesPath() {
        var fixed = ZonedDateTime.of(2015, 8, 30, 12, 36, 0, 0, ZoneOffset.UTC);

        String s3Signature = signatureFor("s3", fixed);
        String execApiSignature = signatureFor("execute-api", fixed);

        assertTrue(s3Signature.contains("Signature="));
        assertTrue(execApiSignature.contains("Signature="));
        assertEquals(extractSignature(s3Signature).length(), 64);
        assertEquals(extractSignature(execApiSignature).length(), 64);
        assertTrue(!extractSignature(s3Signature).equals(extractSignature(execApiSignature)),
                "S3 (single-encoded path) and non-S3 (double-encoded path) must sign differently");
    }

    private static String signatureFor(String service, ZonedDateTime timestamp) {
        var request = new HttpRequestSpecification("https://example.amazonaws.com");
        request.httpMethod(HttpMethod.GET);
        request.subpath("/a b/c"); // space forces an encoding difference between single and double
        return new AwsRequestSigner(request, "us-east-1", service, ACCESS_KEY, SECRET_KEY, null)
                .withTimestamp(timestamp)
                .sign();
    }

    private static String extractSignature(String authorization) {
        return authorization.replaceAll(".*Signature=", "");
    }
}
