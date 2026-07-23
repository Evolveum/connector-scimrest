/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.schema.BaseValueMappingBuilder;
import com.evolveum.polygon.conndev.schema.ValueTypeOverrideMapping;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.ScriptedSingleAttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.HashMap;
import java.util.Map;

public class MappedAttributeBuilderImpl implements RestReferenceAttributeBuilder {

    MappedObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    DefinitionValue<Boolean> emulated = DefinitionValue.DEFAULT_FALSE;

    DefinitionValue<String> remoteName;
    String nativeType;


    private ScimBuilder scim;
    /** Mirrors {@link ConnIdBuilder#name}'s resolved value for callers outside the ConnIdMapping (e.g. build()). */
    String connIdName;
    /** Mirrors {@link ConnIdBuilder#type}'s resolved value for JsonBuilder/ScimBuilder value-mapping resolution. */
    Class<?> connIdType;

    private final ConnIdBuilder connIdMapping = new ConnIdBuilder();

    public Deferred.Settable<MappedAttribute> deffered = Deferred.settable();
    private String referencedObjectClass;
    private boolean isReference = false;
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public MappedAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = DefinitionValue.defaultFrom(name);
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public MappedAttributeBuilderImpl protocolName(DefinitionValue<String> protocolName) {
        json().name(protocolName.value());
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl remoteName(DefinitionValue<String> remoteName) {
        this.remoteName = this.remoteName.moreSpecific(remoteName);
        return this;
    }

    /** The native protocol type as declared by the remote system (e.g. SCIM {@code dateTime}). */
    public MappedAttributeBuilderImpl nativeType(String nativeType) {
        this.nativeType = nativeType;
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl emulated(DefinitionValue<Boolean> emulated) {
        this.emulated = this.emulated.moreSpecific(emulated);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl complexType(DefinitionValue<String> objectClass) {
        connId().type(EmbeddedObject.class);
        connId().roleInReference(DefinitionValue.detected(AttributeInfo.RoleInReference.SUBJECT.toString()));
        connId().referencedObjectClassName(objectClass);
        json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), objectClass.value()));
        return this;
    }

    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
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
        return connIdMapping;
    }

    @Override
    public ScimMapping scim() {
        return (ScimBuilder) protocolMappings.computeIfAbsent(ScimAttributeMapping.class, m -> new ScimBuilder());
    }

    @Override
    public ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    @Override
    public MappedAttributeBuilderImpl objectClass(String objectClass) {
        isReference = true;
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl role(String role) {
        this.isReference = true;
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl role(AttributeInfo.RoleInReference role) {
        return role(role.toString());
    }


    public boolean isReference() {
        return isReference;
    }

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public MappedAttribute build() {
        // FIXME: Could this be part of ConnID schema contributor?
        if (Uid.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        if (Name.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        return new MappedAttribute(this);
    }

    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated(DefinitionValue.detected(true));
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }

    /**
     * Backs {@link #connId()} with a persistent instance (not a fresh object per call) so that
     * {@code DefinitionValue}'s {@code moreSpecific()} merge tracking accumulates correctly across
     * multiple {@code connId { ... }} invocations, matching conndev's own
     * {@code AbstractAttributeBuilder.ConnIdBuilder} shape.
     */
    class ConnIdBuilder implements ConnIdMapping {

        private DefinitionValue<String> name = DefinitionValue.emptyDefault();
        private DefinitionValue<String> nativeName = DefinitionValue.emptyDefault();
        private DefinitionValue<Class<?>> type = DefinitionValue.emptyDefault();
        private DefinitionValue<Boolean> readable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> required = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<String> description = DefinitionValue.emptyDefault();
        private DefinitionValue<Boolean> returnedByDefault = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> multiValued = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> creatable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> updatable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<String> roleInReference = DefinitionValue.emptyDefault();
        private DefinitionValue<String> referencedObjectClassName = DefinitionValue.emptyDefault();
        private DefinitionValue<String> subtype = DefinitionValue.emptyDefault();

        @Override
        public ConnIdMapping name(DefinitionValue<String> name) {
            this.name = this.name.moreSpecific(name);
            connIdName = this.name.value();
            connIdBuilder.setName(this.name.value());
            return this;
        }

        @Override
        public ConnIdMapping nativeName(DefinitionValue<String> name) {
            this.nativeName = this.nativeName.moreSpecific(name);
            connIdBuilder.setNativeName(this.nativeName.value());
            return this;
        }

        @Override
        public ConnIdMapping type(DefinitionValue<Class<?>> connIdType) {
            this.type = this.type.moreSpecific(connIdType);
            MappedAttributeBuilderImpl.this.connIdType = this.type.value();
            connIdBuilder.setType(this.type.value());
            return this;
        }

        @Override
        public ConnIdMapping readable(DefinitionValue<Boolean> readable) {
            this.readable = this.readable.moreSpecific(readable);
            connIdBuilder.setReadable(this.readable.value());
            if (!this.readable.value()) {
                connIdBuilder.setReturnedByDefault(false);
            }
            return this;
        }

        @Override
        public ConnIdMapping required(DefinitionValue<Boolean> required) {
            this.required = this.required.moreSpecific(required);
            connIdBuilder.setRequired(this.required.value());
            return this;
        }

        @Override
        public ConnIdMapping description(DefinitionValue<String> description) {
            this.description = this.description.moreSpecific(description);
            connIdBuilder.setDescription(this.description.value());
            return this;
        }

        @Override
        public ConnIdMapping returnedByDefault(DefinitionValue<Boolean> returnedByDefault) {
            this.returnedByDefault = this.returnedByDefault.moreSpecific(returnedByDefault);
            connIdBuilder.setReturnedByDefault(this.returnedByDefault.value());
            return this;
        }

        @Override
        public ConnIdMapping multiValued(DefinitionValue<Boolean> multiValued) {
            this.multiValued = this.multiValued.moreSpecific(multiValued);
            connIdBuilder.setMultiValued(this.multiValued.value());
            return this;
        }

        @Override
        public ConnIdMapping creatable(DefinitionValue<Boolean> creatable) {
            this.creatable = this.creatable.moreSpecific(creatable);
            connIdBuilder.setCreateable(this.creatable.value());
            return this;
        }

        @Override
        public ConnIdMapping updatable(DefinitionValue<Boolean> updatable) {
            this.updatable = this.updatable.moreSpecific(updatable);
            connIdBuilder.setUpdateable(this.updatable.value());
            return this;
        }

        @Override
        public ConnIdMapping roleInReference(DefinitionValue<String> detected) {
            this.roleInReference = this.roleInReference.moreSpecific(detected);
            connIdBuilder.setRoleInReference(this.roleInReference.value());
            return this;
        }

        @Override
        public ConnIdMapping referencedObjectClassName(DefinitionValue<String> complexType) {
            this.referencedObjectClassName = this.referencedObjectClassName.moreSpecific(complexType);
            connIdBuilder.setReferencedObjectClassName(this.referencedObjectClassName.value());
            return this;
        }

        @Override
        public ConnIdMapping subtype(DefinitionValue<String> from) {
            this.subtype = this.subtype.moreSpecific(from);
            connIdBuilder.setSubtype(this.subtype.value());
            return this;
        }

        @Override
        public DefinitionValue<Class<?>> type() {
            return type;
        }
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
            var extensionUri = MappedAttributeBuilderImpl.this.objectClass.scim().extensionUriFromAlias(uriOrAlias);
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
