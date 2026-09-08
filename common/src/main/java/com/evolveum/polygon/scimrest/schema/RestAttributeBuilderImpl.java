/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.JavaPathFormat;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.schema.AttributeProtocolMappingBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BasePathBuilder;
import com.evolveum.polygon.conndev.schema.BaseValueMappingBuilder;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import tools.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class RestAttributeBuilderImpl extends BaseAttributeBuilder<
        RestAttributeBuilderImpl,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        RestReferenceAttributeBuilder,
        RestAttributeDefinition> implements RestReferenceAttributeBuilder {

    private final RestObjectClassDefinitionBuilder mappedObjectClass;

    @Override
    public String name() {
        return name.value();
    }

    String nativeType;
    ScimBuilder scim;

    public RestAttributeBuilderImpl(RestObjectClassDefinitionBuilder parent, DefinitionValue<String> name) {
        super(parent, name);
        this.mappedObjectClass = parent;
    }

    @Override
    public RestAttributeBuilderImpl nativeType(String nativeType) {
        this.nativeType = nativeType;
        return this;
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            scim = new ScimBuilder();
            protocolMappings.put(ScimAttributeMapping.class, scim);
        }
        return scim;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    @Override
    protected RestAttributeDefinition newDefinition() {
        return new RestAttributeDefinition(this);
    }

    /**
     * SCIM-specific protocol mapping, registered into the inherited {@code protocolMappings} map
     * (mirrors connector-sql's own {@code .sql()}) so the attribute definition builder can derive
     * the ConnId type from {@link ScimAttributeMapping#connIdType()} when none is explicitly declared.
     */
    class ScimBuilder implements AttributeProtocolMappingBuilder, ScimMapping {
        private AttributePathDeclaration<?,?> path;
        private String type;
        private ValueMapping implementation;

        @Override
        public String name() {
            if (path != null && path.actual().onlyAttribute() != null) {
                return path.actual().onlyAttribute().name();
            }

            return null;
        }

        @Override
        public ScimMapping name(String name) {
            return path(AttributePath.of(name));
        }

        @Override
        public ScimMapping type(String name) {
            this.type = name;
            return this;
        }


        @Override
        public AttributePathDeclaration<?,?> path() {
            return path;
        }

        @Override
        public ScimMapping path(String path) {
            // FIXME: Implement parsing of SCIM paths to AttributePath
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ScimMapping path(
                @DelegatesTo(value = RestAttributeBuilder.ScimPathBuilder.class, strategy = Closure.DELEGATE_ONLY)
                @Script.Initialization
                Closure<?> closure) {
            var builder = new PathBuilder();
            GroovyClosures.callAndReturnDelegate(closure, builder);
            this.path = builder.build();
            return this;
        }

        @Override
        public ScimMapping path(AttributePath path) {
            this.path = AttributePathDeclaration.of(JavaPathFormat.INSTANCE, path);
            return this;
        }

        @Override
        public AttributePath extension(String uriOrAlias) {
            var extensionUri = mappedObjectClass.scim().extensionUriFromAlias(uriOrAlias);
            return AttributePath.of(new AttributePath.Extension(extensionUri));
        }


        @Override
        public MappingTableBuilder mappingTable() {
            // FIXME: Implement later
            return null;
        }

        @Override
        public MappingTableBuilder mappingTable(Closure<?> closure) {
            // FIXME: Implement later
            return null;
        }

        @Override
        public ScimBuilder implementation(ValueMapping<?,JsonNode> mapping) {
            this.implementation = mapping;
            return this;
        }

        @Override
        public ScimMapping implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure) {
            Class<?> typeClass = connId().type().value() != null ? connId().type().value() : Object.class;
            var builder = new BaseValueMappingBuilder<>(typeClass, JsonNode.class);
            GroovyClosures.callAndReturnDelegate(closure, builder);
            this.implementation = builder.build();
            return this;
        }

        @Override
        public AttributeProtocolMapping<?,?> build() {
            if (type != null && implementation == null) {
                implementation =  OpenApiValueMapping.from(type, null);
            }
            if (implementation != null) {
                // Adapt the mapping to the attribute's final ConnId type via the shared hook
                // (e.g. a non-string SCIM implementation backing a uid/name presented as String).
                implementation = connId().overrideMappingIfNeeded(implementation);
                return new ScimAttributeMapping(path, implementation);
            }
            return null;
        }

        @Override
        public Class<?> suggestedConnIdType() {
            if (implementation != null) {
                return implementation.connIdType();
            }
            if (type != null) {
                return OpenApiValueMapping.from(type, null).connIdType();
            }
            return null;
        }
    }

    private static class PathBuilder extends BasePathBuilder implements ScimPathBuilder {

    }
}
