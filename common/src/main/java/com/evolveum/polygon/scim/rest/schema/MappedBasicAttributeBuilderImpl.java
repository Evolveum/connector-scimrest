/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.OpenApiValueMapping;
import com.evolveum.polygon.scim.rest.ValueMapping;
import com.evolveum.polygon.scim.rest.api.AttributePath;
import com.evolveum.polygon.scim.rest.groovy.GroovyClosures;
import com.evolveum.polygon.scim.rest.groovy.api.ValueMappingBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

import java.util.HashMap;
import java.util.Map;

public abstract class MappedBasicAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestAttributeBuilder {

    MappedObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    boolean emulated = false;

    String remoteName;
    private String description;


    private ScimBuilder scim;
    String connIdName;
    Class<?> connIdType;

    public MappedBasicAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public MappedBasicAttributeBuilderImpl protocolName(String protocolName) {
        json().name(protocolName);
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    @Override
    public void emulated(boolean emulated) {
        this.emulated = emulated;
    }

    @Override
    public MappedBasicAttributeBuilderImpl readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }


    @Override
    public MappedBasicAttributeBuilderImpl required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl updateable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    public MappedBasicAttributeBuilderImpl description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl multiValued(boolean multiValued) {
        connIdBuilder.setMultiValued(multiValued);
        return this;
    }



    @Override
    public JsonMapping json() {
        return (JsonBuilder) protocolMappings.computeIfAbsent(JsonAttributeMapping.class, m -> new JsonBuilder());
    }

    @Override
    public JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure,json());
    }

    @Override
    public ConnIdMapping connId() {
        return new ConnIdMapping() {
            @Override
            public ConnIdMapping name(String name) {
                MappedBasicAttributeBuilderImpl.this.connIdName = name;
                MappedBasicAttributeBuilderImpl.this.connIdBuilder.setName(name);
                return this;
            }

            @Override
            public ConnIdMapping type(Class<?> connIdType) {
                MappedBasicAttributeBuilderImpl.this.connIdType = connIdType;
                MappedBasicAttributeBuilderImpl.this.connIdBuilder.setType(connIdType);
                return this;
            }
        };
    }

    @Override
    public ScimMapping scim() {
        return (ScimBuilder) protocolMappings.computeIfAbsent(ScimAttributeMapping.class, m -> new ScimBuilder());
    }

    @Override
    public ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    class JsonBuilder implements AttributeProtocolMappingBuilder, JsonMapping {

        private String name;
        private String type;
        private String openApiFormat;


        JsonBuilder() {
            this.name = remoteName;
        }

        @Override
        public JsonMapping name(String protocolName) {
            this.name = name;
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public JsonMapping type(String jsonType) {
            type = jsonType;
            return this;
        }

        @Override
        public JsonMapping openApiFormat(String openapiFormat) {
            this.openApiFormat = openapiFormat;
            return this;
        }

        @Override
        public Class<?> suggestedConnIdType() {
            return null;
        }

        @Override
        public AttributeProtocolMapping<?,?> build() {

            ValueMapping<Object, JsonNode> valueMapping = OpenApiValueMapping.from(type, openApiFormat);
            if (connIdType != null && !connIdType.equals(valueMapping.connIdType())) {
                // we need to to ConnID override
                valueMapping = ValueTypeOverrideMapping.of(connIdType, valueMapping);
            }


            return new JsonAttributeMapping(name, valueMapping);
        }
    }

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
            var extensionUri = MappedBasicAttributeBuilderImpl.this.objectClass.scim().extensionUriFromAlias(uriOrAlias);
            return AttributePath.of(new AttributePath.Extension(extensionUri));
        }


        @Override
        public ScimBuilder implementation(ValueMapping<?,?> mapping) {
            this.implementation = mapping;
            return this;
        }

        @Override
        public ScimMapping implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure) {
            Class<?> typeClass = connIdType != null ? connIdType : Object.class;
            var builder = new ValueMappingBuilderImpl<>(typeClass,JsonNode.class);
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
