/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.RetrievableContext;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.Checks;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;


import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AuthPreferenceManager;
import com.evolveum.polygon.scimrest.groovy.DispatchingAuthorizationCustomizer;

/**
 * The RestContext class provides a context for executing HTTP requests
 * with a configurable HTTP client. It centralizes authorization customization
 * and handles SSL configurations when required.
 *
 * It provides a controlled facade to actual REST client allowing us to customize contracts
 * to be easier used and consumed by AI-assisted workflows.
 */
public class RestContext implements RetrievableContext {

    private static final Log LOG = Log.getLog(RestContext.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final AuthorizationCustomizer<RestClientConfiguration> customizer;
    private final RestClientConfiguration configuration;
    private final HttpClient client;

    public RestContext(RestClientConfiguration configuration, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        this(configuration, customizer, createClient(configuration));
    }

    RestContext(RestClientConfiguration configuration, AuthorizationCustomizer<RestClientConfiguration> customizer, HttpClient client) {
        this.configuration = configuration;
        this.customizer = customizer;
        this.client = client;
    }

    private static HttpClient createClient(RestClientConfiguration configuration) {
        var builder = HttpClient.newBuilder();
        if (Boolean.TRUE.equals(configuration.getTrustAllCertificates())) {
            try {
                var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new ConfigurationException("SSL configuration failed", e);
            }
        }
        builder.connectTimeout(Duration.of(configuration.getTimeoutSeconds().longValue(), ChronoUnit.SECONDS));
        builder.followRedirects(HttpClient.Redirect.NORMAL);
        return builder.build();
    }

    /**
     * Creates a new request initialized with the base address and timeout from the configuration.
     * Authorization is applied when the request is executed via {@link #executeRequest}.
     *
     * @return a new {@code HttpRequestSpecification} with base URI and timeout set.
     */
    public HttpRequestSpecification newRequest() {
        var timeoutSeconds = configuration.getTimeoutSeconds() != null ? configuration.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        var request = new HttpRequestSpecification(configuration.getBaseAddress());
        request.timeout(Duration.of(timeoutSeconds, ChronoUnit.SECONDS));
        return request;
    }

    public boolean isPreferenceActive() {
        return customizer instanceof AuthPreferenceManager<?>;
    }

    @SuppressWarnings("unchecked")
    public void runProbe() {
        if (customizer instanceof AuthPreferenceManager<?> pm) {
            ((AuthPreferenceManager<RestClientConfiguration>) pm).probe(configuration);
        }
    }

    /**
     * Executes an synchronous HTTP request built using the provided {@code RequestBuilder} and parses the response
     * with the specified {@code BodyHandler}.
     *
     * @param <T> the type of the response body
     * @param requestBuilder the builder used to construct the HTTP request
     * @param jsonBodyHandler the handler used to process the response body
     * @return an {@code HttpResponse} containing the response body of type {@code T}
     * @throws URISyntaxException if the URI built by the {@code RequestBuilder} is invalid
     * @throws IOException if an I/O error occurs while sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    public HttpResponse<?> executeRequest(HttpRequestSpecification spec, HttpResponse.BodyHandler<?> jsonBodyHandler) throws IOException, InterruptedException {
        var preAuthCopy = customizer instanceof AuthPreferenceManager<?>
                ? new HttpRequestSpecification(spec) : null;
        customizer.customize(configuration, spec);
        var response = doSend(spec, jsonBodyHandler);

        if (response.statusCode() == 401 && customizer instanceof AuthPreferenceManager<?> pm) {
            @SuppressWarnings("unchecked")
            var typed = (AuthPreferenceManager<RestClientConfiguration>) pm;
            typed.reprobe(configuration);
            customizer.customize(configuration, preAuthCopy);
            LOG.ok("Retrying request after reprobe");
            response = doSend(preAuthCopy, jsonBodyHandler);
        } else if (customizer instanceof DispatchingAuthorizationCustomizer d) {
            d.handleResponse(response);
        }

        return response;
    }

    private HttpResponse<?> doSend(HttpRequestSpecification spec, HttpResponse.BodyHandler<?> handler) throws IOException, InterruptedException {
        HttpRequest request;
        try {
            request = new JdkHttpRequestConverter().convert(spec);
        } catch (ConnectorException e) {
            Checks.checkConfigurationBaseUri(configuration.getBaseAddress());
            throw e;
        }
        LOG.ok("Executing request {0}", request);
        try {
            return client.send(request, handler);
        } catch (IOException e) {
            if (isStaleConnection(e)) {
                // Pooled keep-alive connection was already closed by the other side, so the
                // request was never sent. The pool dropped the dead connection on failure,
                // therefore a single retry is safe even for non-idempotent requests.
                LOG.ok("Stale pooled connection detected ({0}), retrying request once", e.toString());
                return client.send(request, handler);
            }
            throw e;
        }
    }

    /**
     * Returns true if the cause chain contains {@link ClosedChannelException}, which signals
     * that a pooled connection was closed before the request was sent (the server never saw it).
     */
    private static boolean isStaleConnection(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ClosedChannelException) {
                return true;
            }
        }
        return false;
    }


    /**
     * A TrustManager implementation that accepts all TLS/SSL certificates without validation.
     * This manager is used to bypass certificate validation checks during SSL/TLS handshake.
     *
     * This trust manager is only intended if configured to trust all certicates with explicit
     * configuration from user.
     *
     * Note: Using this TrustManager introduces significant security risks as it effectively disables
     * certificate validation, making the application susceptible to man-in-the-middle (MITM) attacks.
     * It should only be used in controlled environments and never in production systems.
     */
    public static final TrustManager TRUST_ALL = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            // empty method
        }

    };
}
