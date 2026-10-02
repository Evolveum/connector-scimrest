/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RestObjectClassDefinitionBuilder extends BaseObjectClassDefinitionBuilder<
        RestObjectClassSchemaBuilder,
        RestObjectClassDefinition,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        RestReferenceAttributeBuilder,
        RestAttributeBuilderImpl,
        RestAttributeDefinition> implements RestObjectClassSchemaBuilder {

    private ScimMapping scim;
    private boolean cached;

    public RestObjectClassDefinitionBuilder(RestSchemaBuilderImpl restSchemaBuilder, DefinitionValue<ObjectClass> name) {
        super(restSchemaBuilder, name);
    }

    @Override
    protected RestAttributeBuilderImpl newAttribute(DefinitionValue<String> def) {
        return new RestAttributeBuilderImpl(this, def);
    }

    @Override
    public RestAttributeBuilderImpl attribute(String name) {
        return (RestAttributeBuilderImpl) super.attribute(name);
    }

    @Override
    public RestAttributeBuilderImpl reference(String name) {
        return (RestAttributeBuilderImpl) super.reference(name);
    }

    /**
     * The SCIM half of the "NAME defaults to a copy of UID" rule (see
     * {@code NameDefaultsToUidRule}): the default {@code __NAME__} attribute copies the UID's
     * SCIM path and wire type, so it reads the same resource property (the resource {@code id})
     * that {@code __UID__} reads. Only plain path+type copies are made — a custom value-mapping
     * implementation on the UID is not inherited; if the UID has no SCIM path or wire type
     * there is nothing to copy.
     */
    @Override
    public RestAttributeBuilderImpl deriveDefaultNameFromUid(RestAttributeBuilderImpl uidAttribute) {
        var uidScim = uidAttribute.scim;
        if (uidScim == null || uidScim.path() == null || uidScim.type() == null) {
            return null;
        }
        if (!findAttributes(a -> Name.NAME.equals(a.name())).isEmpty()) {
            return null;
        }
        var nameAttribute = attribute(Name.NAME);
        nameAttribute.scim();
        nameAttribute.scim.copyFrom(uidScim);
        return nameAttribute;
    }

    @Override
    protected RestObjectClassDefinition buildImpl(ObjectClassInfo connIdInfo, Map<String, RestAttributeDefinition> nativeAttrs, Map<String, RestAttributeDefinition> connIdAttrs) {
        var scimMapping = scim != null
                ? new RestObjectClassDefinition.ObjectClassScimMapping(scim.name(), scim.schemaUri(), scim.flattenAttributes())
                : null;
        return new RestObjectClassDefinition(connIdInfo, nativeAttrs, connIdAttrs, scimMapping, cached);
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            this.scim = new ScimBuilder();
            scim.name(objectClass().getObjectClassValue());
        }
        return this.scim;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(ScimMapping.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    @Override
    public RestObjectClassSchemaBuilder cached(boolean value) {
        this.cached = value;
        return this;
    }

    @Override
    public boolean isCached() {
        return cached;
    }

    private static class ScimBuilder implements ScimMapping {

        private String schemaUri;
        private String name;
        private boolean onlyExplicitlyListed = false;
        private Map<String, ExtensionBuilder> extensions = new LinkedHashMap<>();
        private final List<String> flatten = new ArrayList<>();

        @Override
        public ScimMapping extension(String alias, String namespace) {
            extensions.computeIfAbsent(alias, a -> new ExtensionBuilder(namespace));
            return this;
        }

        @Override
        public ExtensionMapping extension(String alias, String namespace, Closure<?> closure) {
            var extension = extensions.computeIfAbsent(alias, a -> new ExtensionBuilder(namespace));
            if (closure != null) {
                GroovyClosures.callAndReturnDelegate(closure, extension);
            }
            return extension;
        }

        @Override
        public ScimMapping extension(String alias, String namespace, List<String> flattenAttributes) {
            var extension = extensions.computeIfAbsent(alias, a -> new ExtensionBuilder(namespace));
            for (var attribute : flattenAttributes) {
                extension.flatten(attribute);
            }
            return this;
        }

        @Override
        public List<ExtensionFlattening> extensionFlattens() {
            var result = new ArrayList<ExtensionFlattening>();
            for (var entry : extensions.entrySet()) {
                if (!entry.getValue().flatten.isEmpty()) {
                    result.add(new ExtensionFlattening(entry.getKey(), entry.getValue().namespace,
                            entry.getValue().flattenAttributes()));
                }
            }
            return List.copyOf(result);
        }

        @Override
        public String schemaUri() {
            return schemaUri;
        }

        @Override
        public ScimMapping schemaUri(String schemaUri) {
            this.schemaUri = schemaUri;
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ScimMapping name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean isOnlyExplicitlyListed() {
            return onlyExplicitlyListed;
        }

        @Override
        public ScimMapping onlyExplicitlyListed(boolean value) {
            onlyExplicitlyListed = value;
            return this;
        }

        @Override
        public ScimMapping flatten(String attribute) {
            if (attribute == null || attribute.isBlank()) {
                throw new IllegalArgumentException("The SCIM attribute to flatten must be a non-blank name");
            }
            if (!flatten.contains(attribute)) {
                flatten.add(attribute);
            }
            return this;
        }

        @Override
        public List<String> flattenAttributes() {
            return List.copyOf(flatten);
        }

        @Override
        public String extensionUriFromAlias(String uriOrAlias) {
            if (uriOrAlias.startsWith("urn:")) {
                return uriOrAlias;
            }
            var extension = extensions.get(uriOrAlias);
            return extension != null ? extension.namespace : uriOrAlias;
        }

        /**
         * The per-extension state of the object-class SCIM mapping: the declared schema URI and
         * the complex attributes configured to be flattened (see {@link ExtensionMapping}).
         */
        private static class ExtensionBuilder implements ExtensionMapping {

            private final String namespace;
            private final List<String> flatten = new ArrayList<>();

            ExtensionBuilder(String namespace) {
                this.namespace = namespace;
            }

            @Override
            public ExtensionMapping flatten(String attribute) {
                if (attribute == null || attribute.isBlank()) {
                    throw new IllegalArgumentException("The SCIM attribute to flatten must be a non-blank name");
                }
                if (!flatten.contains(attribute)) {
                    flatten.add(attribute);
                }
                return this;
            }

            @Override
            public List<String> flattenAttributes() {
                return List.copyOf(flatten);
            }
        }
    }
}
