/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.rest;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;

import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for the single retry on a stale pooled connection ({@link ClosedChannelException})
 * in {@link RestContext#executeRequest}.
 */
public class RestContextStaleConnectionTest {

    private static final RestClientConfiguration CONFIGURATION = new RestClientConfiguration() {
        @Override
        public String getBaseAddress() {
            return "http://localhost:65535";
        }

        @Override
        public Boolean getTrustAllCertificates() {
            return false;
        }

        @Override
        public String getRestTestEndpoint() {
            return "/test";
        }
    };

    /**
     * IOException with ClosedChannelException in the cause chain, mimicking what JDK HttpClient
     * throws when a pooled keep-alive connection was closed by the other side.
     */
    private static IOException staleConnectionException() {
        ConnectException inner = new ConnectException("Connection failed");
        inner.initCause(new ClosedChannelException());
        ConnectException outer = new ConnectException("Connection failed");
        outer.initCause(inner);
        return outer;
    }

    @Test
    public void staleConnectionIsRetriedOnceAndSucceeds() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient(staleConnectionException(), 200);
        RestContext context = new RestContext(CONFIGURATION, (c, s) -> {}, client);

        HttpResponse<?> response = context.executeRequest(context.newRequest(), HttpResponse.BodyHandlers.discarding());

        assertEquals(response.statusCode(), 200);
        assertEquals(client.sendCount, 2, "Request should be sent exactly twice (original + one retry)");
    }

    @Test
    public void staleConnectionIsRetriedOnlyOnce() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient(staleConnectionException(), staleConnectionException(), 200);
        RestContext context = new RestContext(CONFIGURATION, (c, s) -> {}, client);

        try {
            context.executeRequest(context.newRequest(), HttpResponse.BodyHandlers.discarding());
            fail("Expected IOException was not thrown");
        } catch (IOException e) {
            assertTrue(hasCause(e, ClosedChannelException.class),
                    "Expected ClosedChannelException in cause chain but got: " + e);
        }
        assertEquals(client.sendCount, 2, "Request should be sent exactly twice (original + one retry), no retry loop");
    }

    @Test
    public void otherIOExceptionIsNotRetried() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient(new ConnectException("Connection refused"), 200);
        RestContext context = new RestContext(CONFIGURATION, (c, s) -> {}, client);

        try {
            context.executeRequest(context.newRequest(), HttpResponse.BodyHandlers.discarding());
            fail("Expected IOException was not thrown");
        } catch (IOException e) {
            assertEquals(e.getMessage(), "Connection refused");
        }
        assertEquals(client.sendCount, 1, "Request should be sent exactly once, other IOExceptions must not be retried");
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> cause) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (cause.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * HttpClient stub with scripted outcomes per send() call: an IOException to be thrown
     * or an Integer status code to be returned.
     */
    private static class ScriptedHttpClient extends HttpClient {

        private final Deque<Object> outcomes = new ArrayDeque<>();
        private int sendCount;

        private ScriptedHttpClient(Object... scriptedOutcomes) {
            for (Object outcome : scriptedOutcomes) {
                outcomes.add(outcome);
            }
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            sendCount++;
            Object outcome = outcomes.remove();
            if (outcome instanceof IOException e) {
                throw e;
            }
            return response(request, (Integer) outcome);
        }

        private static <T> HttpResponse<T> response(HttpRequest request, int statusCode) {
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return statusCode;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (n, v) -> true);
                }

                @Override
                public T body() {
                    return null;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<java.net.ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }
    }
}
