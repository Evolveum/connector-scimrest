/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.schema.AttributeProtocolMappingBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseValueMappingBuilder;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.scimrest.groovy.ScriptedSingleAttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class MappedAttributeBuilderImpl extends BaseAttributeBuilder<
        MappedAttributeBuilderImpl,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        RestReferenceAttributeBuilder,
        MappedAttribute> implements RestReferenceAttributeBuilder {

    private final MappedObjectClassBuilder mappedObjectClass;

    String nativeType;
    ScimBuilder scim;

    public Deferred.Settable<MappedAttribute> deffered = Deferred.settable();
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public MappedAttributeBuilderImpl(MappedObjectClassBuilder parent, DefinitionValue<String> name) {
        super(parent, name);
        this.mappedObjectClass = parent;
    }

    /** The native protocol type as declared by the remote system (e.g. SCIM {@code dateTime}). */
    public MappedAttributeBuilderImpl nativeType(String nativeType) {
        this.nativeType = nativeType;
        return this;
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            scim = new ScimBuilder();
        }
        return scim;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    @Override
    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated(DefinitionValue.detected(true));
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(mappedObjectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }

    @Override
    public MappedAttribute build() {
        return new MappedAttribute(this);
    }

    /**
     * SCIM-specific protocol mapping. Not stored via the inherited {@code protocolMappings} map
     * (package-private on {@code AbstractAttributeBuilder}, unreachable from here) - mirrors
     * connector-sql's own {@code .sql()} in keeping its own dedicated field instead.
     */
    class ScimBuilder implements AttributeProtocolMappingBuilder, ScimMapping {
        private AttributePath path;
        private String type;
        private ValueMapping implementation;

        @Override
        public String name() {
            if (path != null && path.onlyAttribute() != null) {
                return path.onlyAttribute().name();
            }

            return null;
        }

        @Override
        public ScimMapping name(String name) {
            this.path = AttributePath.of(name);
            return this;
        }

        @Override
        public ScimMapping type(String name) {
            this.type = name;
            return this;
        }


        @Override
        public AttributePath path() {
            return path;
        }

        @Override
        public ScimMapping path(String path) {
            // FIXME: Implement parsing of SCIM paths to AttributePath
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ScimMapping path(AttributePath path) {
            this.path = path;
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
                return new ScimAttributeMapping(path, implementation);
            }
            return null;
        }

        @Override
        public Class<?> suggestedConnIdType() {
            return null;
        }
    }
}
