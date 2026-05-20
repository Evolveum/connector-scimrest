package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.ConfigurationMixin;
import com.evolveum.polygon.scimrest.impl.rest.JdkHttpRequestConverter;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class AuthPreferenceManager<C extends ConfigurationMixin> implements AuthorizationCustomizer<C> {

    @FunctionalInterface
    public interface TypeApplier<C> {
        boolean apply(Class<? extends C> type, C configuration, HttpRequestSpecification request);
    }

    private static final Log LOG = Log.getLog(AuthPreferenceManager.class);

    private final List<Class<? extends C>> preferenceOrder;
    private final TypeApplier<C> typeApplier;
    private final Function<C, String> probeBaseUrlExtractor;
    private final Function<C, String> probeSubpathExtractor;
    private final Predicate<C> trustAllPredicate;

    private volatile Class<? extends C> activeMethod = null;
    private volatile HttpClient httpClient = null;

    public AuthPreferenceManager(
            List<Class<? extends C>> preferenceOrder,
            TypeApplier<C> typeApplier,
            Function<C, String> probeBaseUrlExtractor,
            Function<C, String> probeSubpathExtractor,
            Predicate<C> trustAllPredicate) {
        this.preferenceOrder = preferenceOrder;
        this.typeApplier = typeApplier;
        this.probeBaseUrlExtractor = probeBaseUrlExtractor;
        this.probeSubpathExtractor = probeSubpathExtractor;
        this.trustAllPredicate = trustAllPredicate;
    }

    @Override
    public void customize(C configuration, HttpRequestSpecification request) {
        ensureActive(configuration);
        typeApplier.apply(activeMethod, configuration, request);
    }

    /** Explicitly runs the probe — used by test() so the probe IS the test. */
    public void probe(C configuration) {
        synchronized (this) {
            activeMethod = null;
            doProbe(configuration);
        }
    }

    public void reprobe(C configuration) {
        synchronized (this) {
            activeMethod = null;
            doProbe(configuration);
        }
    }

    public Class<? extends C> getActiveMethod() {
        return activeMethod;
    }

    private synchronized void ensureActive(C configuration) {
        if (activeMethod != null) return;
        doProbe(configuration);
    }

    private void doProbe(C configuration) {
        if (httpClient == null) {
            httpClient = buildClient(trustAllPredicate.test(configuration));
        }

        var baseUrl = probeBaseUrlExtractor.apply(configuration);
        var subpath = probeSubpathExtractor.apply(configuration);

        if (baseUrl == null || subpath == null) {
            activeMethod = preferenceOrder.get(0);
            LOG.ok("Auth preference: no probe URL configured, defaulting to ''{0}''", activeMethod.getSimpleName());
            return;
        }

        for (var type : preferenceOrder) {
            var probeSpec = new HttpRequestSpecification(baseUrl);
            probeSpec.subpath(subpath);
            if (!typeApplier.apply(type, configuration, probeSpec)) {
                LOG.ok("Auth preference: ''{0}'' not applicable for this configuration, skipping", type.getSimpleName());
                continue;
            }
            try {
                var jdkRequest = new JdkHttpRequestConverter().convert(probeSpec);
                var response = httpClient.send(jdkRequest, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 400) {
                    activeMethod = type;
                    LOG.ok("Auth preference: selected ''{0}'' (HTTP {1})", type.getSimpleName(), response.statusCode());
                    return;
                }
                LOG.ok("Auth preference: ''{0}'' returned HTTP {1}, trying next", type.getSimpleName(), response.statusCode());
            } catch (IOException | InterruptedException e) {
                LOG.ok("Auth preference: ''{0}'' probe failed: {1}, trying next", type.getSimpleName(), e.getMessage());
            }
        }

        var tried = preferenceOrder.stream().map(Class::getSimpleName).toList();
        throw new InvalidCredentialException("No configured auth method succeeded during probe. Tried: " + tried);
    }

    private static HttpClient buildClient(boolean trustAll) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (trustAll) {
            try {
                var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{RestContext.TRUST_ALL}, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new ConnectorException("SSL configuration failed for auth preference manager", e);
            }
        }
        return builder.build();
    }
}
