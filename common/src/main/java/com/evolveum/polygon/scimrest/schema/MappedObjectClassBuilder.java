/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MappedObjectClassBuilder implements RestObjectClassSchemaBuilder {

    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }

    private final String name;
    private final ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();
    private final RestSchemaBuilderImpl parent;
    Map<String, MappedAttributeBuilderImpl> nativeAttributes = new HashMap<>();
    private DefinitionValue<String> description = DefinitionValue.emptyDefault();
    private DefinitionValue<Boolean> embedded = DefinitionValue.DEFAULT_FALSE;
    private String locator;
    private String namespace;
    private ScimMapping scim;

    public MappedObjectClassBuilder(RestSchemaBuilderImpl restSchemaBuilder, String name) {
        this.name = name;
        this.parent = restSchemaBuilder;
        connIdBuilder.setType(name);
    }

    @Override
    public MappedAttributeBuilderImpl attribute(String name) {
        return nativeAttributes.computeIfAbsent(name, (k) -> new MappedAttributeBuilderImpl(this, k));
    }

    @Override
    public MappedAttributeBuilderImpl reference(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> {
            var ret = new MappedAttributeBuilderImpl(MappedObjectClassBuilder.this, k);
            ret.connId().type(ConnectorObjectReference.class);
            return ret;
        });
        return (MappedAttributeBuilderImpl) builder;
    }

    @Override
    public MappedObjectClassBuilder embedded(DefinitionValue<Boolean> embedded) {
        this.embedded = this.embedded.moreSpecific(embedded);
        connIdBuilder.setEmbedded(this.embedded.value());
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl attribute(String name, @DelegatesTo(RestAttributeBuilder.class) Closure closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public MappedAttributeBuilderImpl reference(String name, @DelegatesTo(RestReferenceAttributeBuilder.class) Closure closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public MappedObjectClassBuilder description(DefinitionValue<String> description) {
        this.description = this.description.moreSpecific(description);
        return this;
    }

    /** Where the object class lives in the remote system — for SCIM the resource endpoint. */
    public MappedObjectClassBuilder locator(String locator) {
        this.locator = locator;
        return this;
    }

    /** Namespace of the object class — for SCIM the schema URN. */
    public MappedObjectClassBuilder namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            this.scim = new ScimBuilder();
            scim.name(name);
        }
        return this.scim;
        
    }

    public String name() {
        return name;
    }

    @Override
    public MappedObjectClassBuilder self() {
        return this;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(ScimMapping.class)  Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    public MappedObjectClassBuilder connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = attribute(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connId().name(finalName);
        return this;
    }


    public MappedObjectClass build() {
        var connIdAttrs = new HashMap<String, MappedAttribute>();
        var nativeAttrs = new HashMap<String, MappedAttribute>();
        for (var attrBuilder : nativeAttributes.values()) {
            var attribute = attrBuilder.build();
            connIdBuilder.addAttributeInfo(attribute.connId());
            connIdAttrs.put(attribute.connId().getName(), attribute);
            nativeAttrs.put(attribute.remoteName(), attribute);
        }

        var objectClass = new MappedObjectClass(connIdBuilder.build(), nativeAttrs, connIdAttrs);
        objectClass.locator(locator);
        objectClass.namespace(namespace);
        return objectClass;
    }

    public String description() {
        return description.value();
    }

    public boolean embedded() {
        return embedded.value();
    }

    public Iterable<MappedAttributeBuilderImpl> allAttributes() {
        return nativeAttributes.values();
    }

    @Override
    public List<RestAttributeBuilder<RestReferenceAttributeBuilder>> findAttributes(Predicate<RestAttributeBuilder<RestReferenceAttributeBuilder>> predicate) {
        return nativeAttributes.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public boolean connIdAttributeNotDefined(String name) {
        for (var attrBuilder : nativeAttributes.values()) {
            if (name.equals(attrBuilder.connIdBuilder.build().getName())) {
                return false;
            }
        }
        return true;
    }

    public ContextLookup contextLookup() {
        return parent.contextLookup();
    }


    private static class ScimBuilder implements ScimMapping {


        private String schemaUri;
        private String name;
        private boolean onlyExplicitlyListed = false;
        private Map<String, String> aliasToNamespace = new HashMap<>();

        @Override
        public ScimMapping extension(String alias, String namespace) {
            aliasToNamespace.put(alias, namespace);
            return this;
        }

        @Override
        public String schemaUri() {
            return schemaUri;
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
        public String extensionUriFromAlias(String uriOrAlias) {
            if (uriOrAlias.startsWith("urn:")) {
                return uriOrAlias;
            }
            return aliasToNamespace.getOrDefault(uriOrAlias, uriOrAlias);
        }
    }
}
