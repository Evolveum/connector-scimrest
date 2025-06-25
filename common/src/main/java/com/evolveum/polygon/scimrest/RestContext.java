/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;


import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

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

    private final AuthorizationCustomizer customizer;
    private final RestClientConfiguration configuration;
    private final HttpClient client;

    public RestContext(RestClientConfiguration configuration, AuthorizationCustomizer customizer) {
        this.configuration = configuration;
        var builder = HttpClient.newBuilder();
        if (Boolean.TRUE.equals(configuration.getTrustAllCertificates())) {
            try {
                var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException(e);
            }
        }
        this.client = builder.build();
        this.customizer = customizer;
    }

    /**
     * Creates and returns a new request with authorization added using the base address from the configuration
     * and applying any customizations defined by the provided {@code AuthorizationCustomizer}.
     *
     * @return a new instance of {@code RequestBuilder} that has been initialized with the base URI
     *         and customized for authorization.
     */
    public RequestBuilder newAuthorizedRequest() {
        var request = new RequestBuilder(configuration.getBaseAddress());
        this.customizer.customize(configuration, request);
        return request;
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
    public <T> HttpResponse<T> executeRequest(RequestBuilder requestBuilder, HttpResponse.BodyHandler<T> jsonBodyHandler) throws URISyntaxException, IOException, InterruptedException {
        var request = requestBuilder.build();
        LOG.ok("Executing request {0}", request);
        return client.send(request, jsonBodyHandler);
    }

    /**
     * A utility class for building and configuring HTTP requests for RestContext.
     *
     * This class is exposed to Groovy Scripts.
     *
     * This class provides methods to set various components of a request such as the API endpoint, query parameters,
     * headers, and subpaths. Additionally, it simplifies constructing URIs and associating path parameters with
     * placeholders in the endpoint.
     *
     * This class is designed to be used as part of the {@code RestContext} for creating and sending HTTP requests
     * that comply with specific configurations and authorization customizations.
     */
    public static class RequestBuilder {

        private final HttpRequest.Builder request = HttpRequest.newBuilder();
        private final String baseUri;


        public RequestBuilder(String baseUri) {
            this.baseUri = baseUri;
        }

        String apiEndpoint;
        StringBuilder subpath = new StringBuilder();
        Map<String, String> queryParameters = new HashMap<>();

        public HttpRequest build() throws URISyntaxException {
            request.uri(buildUri());
            return request.build();
        }

        public RequestBuilder subpath(String subpath) {
            if (!subpath.isEmpty() && !subpath.endsWith("/")) {
                this.subpath.append("/");
            }
            this.subpath.append(subpath);
            return this;
        }

        private URI buildUri() throws URISyntaxException {
            var builder = new StringBuilder();
            builder.append(baseUri);
            builder.append("/");
            if (apiEndpoint != null) {
                builder.append(apiEndpoint);
            }
            if (!subpath.isEmpty()) {
                if (!builder.toString().endsWith("/")) {
                    builder.append("/");
                }
                builder.append(subpath);
            }
            if (queryParameters != null) {
                builder.append("?");
                for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
                    builder.append(entry.getKey());
                    builder.append("=");
                    builder.append(entry.getValue());
                    builder.append("&");
                }
            }
            return new URI(builder.toString());
        }

        public RequestBuilder apiEndpoint(String endpoint) {
            this.apiEndpoint = endpoint;
            return this;
        }

        public RequestBuilder query(String key, String value) {
            this.queryParameters.put(key, value);
            return this;
        }

        public RequestBuilder queryParameter(String key, String value) {
            this.queryParameters.put(key, value);
            return this;
        }

        public RequestBuilder queryParameter(String key, Number value) {
            this.queryParameters.put(key, value.toString());
            return this;
        }

        public RequestBuilder pathParameter(String key, String value) {
            // FIXME: Replace with real UriTemplates in future
            var keySearch = "{"+key+"}";
            var position = apiEndpoint.indexOf(keySearch);
            if (position < 0) {
                throw new IllegalArgumentException("Path parameter " + key + " not found in " + subpath);
            }
            apiEndpoint = apiEndpoint.replace(keySearch, value);
            return this;
        }

        public RequestBuilder pathParameter(String key, Attribute value) {
            return pathParameter(key, AttributeUtil.getAsStringValue(value));
        }

        public RequestBuilder header(String name, String value) {
            this.request.header(name, value);
            return this;
        }
    }

    /**
     * Defines a mechanism for customizing the authorization of HTTP requests.
     * Implementations of this interface are used to modify the configuration
     * and request details to add specific authorization settings.
     *
     * The {@code customize} method is typically invoked during the construction
     * of HTTP requests in a context where authorization is required. It is
     * responsible for applying authorization headers, tokens, or other
     * necessary configurations to the HTTP request to ensure secure communication.
     *
     * The interface is intended to be implemented by Groovy Connectors if default
     * supported authorization schemes does not work.
     *
     * One such example is Forgejo, which does requires token being prefixed by word token.
     */
    public interface AuthorizationCustomizer {

        /**
         * Customizes the HTTP request configuration and request properties to apply
         * specific authorization logic or additional settings.
         *
         * @param configuration the HTTP client configuration that provides details
         *                       like base address and authorization settings.
         * @param request        the HTTP request builder that can be modified to
         *                       include custom headers, parameters, or other request
         *                       configurations.
         *
         */
        void customize(RestClientConfiguration configuration, RequestBuilder request);
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
