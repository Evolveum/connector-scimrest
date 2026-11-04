/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.JsonUidReferenceMapping;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/** Interprets REST resource links using the connector's configured instance. */
public final class RestUidReferenceMapping extends JsonUidReferenceMapping {
    private final URI linkPrefix;
    private final ContextLookup context;

    private RestUidReferenceMapping(ObjectClass target, Options options) {
        super(target, options);
        linkPrefix = options.prefix;
        context = options.context;
    }

    static Supplier<JsonUidReferenceMapping.Options> optionsFor(ContextLookup context) {
        return () -> new Options(context);
    }

    private static URI linkPrefix(String template) {
        if (!template.endsWith("/{uid}")) {
            throw new IllegalArgumentException("A UID link template must end with /{uid}");
        }
        String prefix = template.substring(0, template.length() - "{uid}".length());
        URI uri = URI.create(prefix);
        if (!isResourceLink(uri) || !uri.getRawPath().endsWith("/")) {
            throw new IllegalArgumentException("Expected a URI path ending in /{uid}: " + template);
        }
        return uri;
    }

    private static boolean isResourceLink(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        if (!uri.isAbsolute()) {
            return uri.getRawAuthority() == null && !uri.toString().startsWith("//");
        }
        return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private URI instanceDirectory() {
        var instance = context == null ? null : context.get(RestConnectorContext.class);
        String address = instance != null && instance.configuration() instanceof RestClientConfiguration config
                ? config.getBaseAddress() : null;
        URI base = address == null || address.isBlank() ? null : URI.create(address);
        if (!isResourceLink(base) || !base.isAbsolute()) {
            throw new IllegalArgumentException("An absolute reference needs a configured HTTP(S) instance address");
        }
        return base.getRawPath().endsWith("/") ? base : URI.create(base + "/");
    }

    private static URI onInstance(URI base, URI link) {
        URI resolved = base.resolve(link);
        if (!base.getScheme().equalsIgnoreCase(resolved.getScheme())
                || !base.getHost().equalsIgnoreCase(resolved.getHost()) || port(base) != port(resolved)) {
            throw new IllegalArgumentException("Reference does not belong to the configured instance");
        }
        return resolved;
    }

    @Override
    protected String decodeUid(String link) {
        URI href = URI.create(link);
        URI prefix = linkPrefix;
        if (!isResourceLink(href)) {
            throw new IllegalArgumentException("Expected a resource link matching " + linkPrefix + "{uid}");
        }
        if (href.isAbsolute() || prefix.isAbsolute()) {
            URI directory = instanceDirectory();
            href = onInstance(directory, href);
            prefix = onInstance(directory, prefix);
        }
        String path = href.getRawPath();
        if (!path.startsWith(prefix.getRawPath())) {
            throw new IllegalArgumentException("Expected a reference matching " + linkPrefix + "{uid}");
        }
        String segment = path.substring(prefix.getRawPath().length());
        if (segment.contains("/")) {
            throw new IllegalArgumentException("Expected one UID path segment in " + link);
        }
        String uid = URI.create("/" + segment).getPath().substring(1);
        if (uid.isBlank()) {
            throw new IllegalArgumentException("Referenced UID is empty");
        }
        rejectDotSegment(uid);
        return uid;
    }

    private static void rejectDotSegment(String uid) {
        if (".".equals(uid) || "..".equals(uid)) {
            throw new IllegalArgumentException("A resource link cannot use a dot segment as its UID: " + uid);
        }
    }

    @Override
    protected String encodeUid(String uid) {
        rejectDotSegment(uid);
        if (linkPrefix.isAbsolute()) {
            onInstance(instanceDirectory(), linkPrefix);
        }
        return linkPrefix + URLEncoder.encode(uid, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static final class Options extends JsonUidReferenceMapping.Options {
        private final ContextLookup context;
        private URI prefix;

        Options(ContextLookup context) {
            this.context = context;
        }

        @Yaml.Key
        public void template(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("A UID link template must be a nonblank string");
            }
            prefix = linkPrefix(value);
        }

        @Override
        public JsonUidReferenceMapping build(ObjectClass target) {
            if (prefix == null) {
                return super.build(target);
            }
            if (!hasUidField()) {
                throw new IllegalArgumentException("A UID link template requires a uidFrom field");
            }
            return new RestUidReferenceMapping(target, this);
        }
    }
}
