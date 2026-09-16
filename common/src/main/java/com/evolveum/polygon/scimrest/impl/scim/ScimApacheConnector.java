/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.identityconnectors.common.logging.Log;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * JAX-RS {@link Connector} backed by Apache HttpClient 5.
 *
 * <p>The JDK {@code HttpURLConnection} (Jersey's default connector) only permits a fixed set of HTTP
 * methods and rejects {@code PATCH}, which the SCIM PATCH update strategy (RFC 7644 section 3.5.2)
 * requires. This connector routes the request through Apache HttpClient 5, which supports arbitrary
 * methods, while still honoring the client-level filters (authentication, schema defaults, error
 * handling) because they run before and after the connector.</p>
 */
public class ScimApacheConnector implements Connector {

    private static final Log LOG = Log.getLog(ScimApacheConnector.class);

    private final CloseableHttpClient httpClient;

    public ScimApacheConnector(SSLContext sslContext) {
        var cmBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        if (sslContext != null) {
            cmBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
        }
        var connectionManager = cmBuilder.build();
        this.httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        var uri = request.getUri();
        try {
            byte[] body = new byte[0];
            if (request.hasEntity()) {
                var buffer = new ByteArrayOutputStream();
                request.setStreamProvider(size -> buffer);
                request.writeEntity();
                body = buffer.toByteArray();
            }

            var httpRequest = createRequest(request.getMethod(), uri);
            request.getRequestHeaders().forEach((name, values) -> values.forEach(value -> httpRequest.addHeader(name, value)));
            // Disable transparent gzip: the JDK/SCIM responses are not compressed, and forcing the
            // decompressing stream onto a plain body throws EOFException.
            if (httpRequest.getFirstHeader("Accept-Encoding") == null) {
                httpRequest.setHeader("Accept-Encoding", "identity");
            }
            if (body.length > 0) {
                var mediaType = request.getMediaType() != null ? request.getMediaType().toString() : "application/octet-stream";
                httpRequest.setEntity(new ByteArrayEntity(body, ContentType.parse(mediaType)));
            }

            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                var statusType = Response.Status.fromStatusCode(response.getCode());
                var clientResponse = new ClientResponse(statusType, request);
                for (Header header : response.getHeaders()) {
                    clientResponse.getHeaders().add(header.getName(), header.getValue());
                }
                var entity = response.getEntity();
                byte[] responseBody = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];
                // Read the body fully so the returned stream is independent of the (soon-closed)
                // Apache response; otherwise Jersey reads a stream that is already closed.
                clientResponse.setEntityStream(new ByteArrayInputStream(responseBody));
                return clientResponse;
            }
        } catch (ProcessingException e) {
            // A genuine JAX-RS processing problem (e.g. unsupported HTTP method) — keep as is.
            throw e;
        } catch (Exception e) {
            // Map network failures (connect/read timeout, reset, TLS, ...) to the ICF types
            // midPoint reacts to correctly (postponed/retried, resource DOWN) instead of
            // flattening them into ProcessingException -> generic ConnectorException.
            throw ScimExceptionMapper.mapFailure(e, uri.toString());
        }
    }

    private static HttpUriRequestBase createRequest(String method, URI uri) {
        return switch (method.toUpperCase()) {
            case "GET" -> new HttpGet(uri);
            case "POST" -> new HttpPost(uri);
            case "PUT" -> new HttpPut(uri);
            case "PATCH" -> new HttpPatch(uri);
            case "DELETE" -> new HttpDelete(uri);
            case "HEAD" -> new HttpHead(uri);
            case "OPTIONS" -> new HttpOptions(uri);
            default -> throw new ProcessingException("Unsupported HTTP method: " + method);
        };
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        var response = apply(request);
        callback.response(response);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "scim-apache-httpclient5";
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (Exception e) {
            // best effort — only log, the client is going away anyway
            LOG.info("Failed to close the SCIM Apache HttpClient: {0}", e.getMessage());
        }
    }
}
