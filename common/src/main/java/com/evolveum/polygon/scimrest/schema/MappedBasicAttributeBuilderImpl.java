/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.schema.BaseValueMappingBuilder;
import com.evolveum.polygon.conndev.schema.ValueTypeOverrideMapping;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.HashMap;
import java.util.Map;

public abstract class MappedBasicAttributeBuilderImpl implements RestAttributeBuilder {

    MappedObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    DefinitionValue<Boolean> emulated = DefinitionValue.DEFAULT_FALSE;

    DefinitionValue<String> remoteName;
    String nativeType;


    private ScimBuilder scim;
    String connIdName;
    Class<?> connIdType;

    private DefinitionValue<Boolean> readable = DefinitionValue.DEFAULT_TRUE;
    private DefinitionValue<Boolean> required = DefinitionValue.DEFAULT_FALSE;
    private DefinitionValue<Boolean> updateable = DefinitionValue.DEFAULT_TRUE;
    private DefinitionValue<Boolean> creatable = DefinitionValue.DEFAULT_TRUE;
    private DefinitionValue<String> description = DefinitionValue.emptyDefault();
    private DefinitionValue<Boolean> returnedByDefault = DefinitionValue.DEFAULT_TRUE;
    private DefinitionValue<Boolean> multiValued = DefinitionValue.DEFAULT_FALSE;
    private DefinitionValue<String> complexType = DefinitionValue.emptyDefault();

    public MappedBasicAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = DefinitionValue.defaultFrom(name);
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public MappedBasicAttributeBuilderImpl protocolName(DefinitionValue<String> protocolName) {
        json().name(protocolName.value());
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl remoteName(DefinitionValue<String> remoteName) {
        this.remoteName = this.remoteName.moreSpecific(remoteName);
        return this;
    }

    /** The native protocol type as declared by the remote system (e.g. SCIM {@code dateTime}). */
    public MappedBasicAttributeBuilderImpl nativeType(String nativeType) {
        this.nativeType = nativeType;
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl emulated(DefinitionValue<Boolean> emulated) {
        this.emulated = this.emulated.moreSpecific(emulated);
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl readable(DefinitionValue<Boolean> readable) {
        this.readable = this.readable.moreSpecific(readable);
        connIdBuilder.setReadable(this.readable.value());
        if (!this.readable.value()) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }


    @Override
    public MappedBasicAttributeBuilderImpl required(DefinitionValue<Boolean> required) {
        this.required = this.required.moreSpecific(required);
        connIdBuilder.setRequired(this.required.value());
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl updateable(DefinitionValue<Boolean> updateable) {
        this.updateable = this.updateable.moreSpecific(updateable);
        connIdBuilder.setUpdateable(this.updateable.value());
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl creatable(DefinitionValue<Boolean> creatable) {
        this.creatable = this.creatable.moreSpecific(creatable);
        connIdBuilder.setCreateable(this.creatable.value());
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl description(DefinitionValue<String> description) {
        this.description = this.description.moreSpecific(description);
        connIdBuilder.setDescription(this.description.value());
        return this;
    }


    @Override
    public MappedBasicAttributeBuilderImpl complexType(DefinitionValue<String> objectClass) {
        this.complexType = this.complexType.moreSpecific(objectClass);
        connId().type(EmbeddedObject.class);
        connIdBuilder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        connIdBuilder.setReferencedObjectClassName(this.complexType.value());
        json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), this.complexType.value()));
        return this;
    }

    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
    }


    @Override
    public MappedBasicAttributeBuilderImpl returnedByDefault(DefinitionValue<Boolean> returnedByDefault) {
        this.returnedByDefault = this.returnedByDefault.moreSpecific(returnedByDefault);
        connIdBuilder.setReturnedByDefault(this.returnedByDefault.value());
        return this;
    }

    @Override
    public MappedBasicAttributeBuilderImpl multiValued(DefinitionValue<Boolean> multiValued) {
        this.multiValued = this.multiValued.moreSpecific(multiValued);
        connIdBuilder.setMultiValued(this.multiValued.value());
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
        private AttributePath path;
        private String type;
        private String openApiFormat;
        private ValueMapping<Object, JsonNode> implementation;


        JsonBuilder() {
            this.name = remoteName.value();
        }

        @Override
        public JsonMapping name(String protocolName) {
            this.name = protocolName;
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
        public JsonMapping path(AttributePath path) {
            this.path = path;
            return this;
        }

        @Override
        public JsonMapping openApiFormat(String openapiFormat) {
            this.openApiFormat = openapiFormat;
            return this;
        }

        @Override
        public JsonMapping implementation(ValueMapping<?, JsonNode> mapping) {
            this.implementation = (ValueMapping) mapping;
            return this;
        }

        @Override
        public JsonMapping implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure) {
            Class<?> typeClass = connIdType != null ? connIdType : Object.class;
            var builder = new BaseValueMappingBuilder<>(typeClass,JsonNode.class);
            GroovyClosures.callAndReturnDelegate(closure, builder);
            this.implementation = (ValueMapping) builder.build();
            return this;
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
        public Class<?> suggestedConnIdType() {
            return null;
        }

        @Override
        public AttributeProtocolMapping<?,?> build() {

            if (this.implementation == null) {
                implementation = OpenApiValueMapping.from(type, openApiFormat);
            }
            if (connIdType != null && !connIdType.equals(implementation.connIdType())) {
                // we need to to ConnID override
                implementation = ValueTypeOverrideMapping.of(connIdType, implementation);
            }
            if (path == null) {
                path = AttributePath.of(name);
            }
            return new JsonAttributeMapping(path, implementation);
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
            Class<?> typeClass = connIdType != null ? connIdType : Object.class;
            var builder = new BaseValueMappingBuilder<>(typeClass,JsonNode.class);
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
