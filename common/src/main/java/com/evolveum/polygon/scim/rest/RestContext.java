package com.evolveum.polygon.scim.rest;

import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;


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

public class RestContext {

    private final AuthorizationCustomizer customizer;
    private final HttpClientConfiguration configuration;
    private final HttpClient client;

    public RestContext(HttpClientConfiguration configuration, AuthorizationCustomizer customizer) {
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

    public RequestBuilder newAuthorizedRequest() {
        var request = new RequestBuilder(configuration.getBaseAddress());
        this.customizer.customize(configuration, request);
        return request;
    }

    public <T> HttpResponse<T> executeRequest(RequestBuilder requestBuilder, HttpResponse.BodyHandler<T> jsonBodyHandler) throws URISyntaxException, IOException, InterruptedException {
        return client.send(requestBuilder.build(), jsonBodyHandler);
    }

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

        public RequestBuilder header(String name, String value) {
            this.request.header(name, value);
            return this;
        }
    }

    public interface AuthorizationCustomizer {
        void customize(HttpClientConfiguration configuration, RequestBuilder request);
    }

    private static final TrustManager TRUST_ALL = new X509ExtendedTrustManager() {
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
