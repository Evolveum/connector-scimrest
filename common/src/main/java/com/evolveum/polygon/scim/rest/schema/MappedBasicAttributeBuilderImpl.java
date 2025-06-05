package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

import java.util.HashMap;
import java.util.Map;

public class MappedBasicAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestAttributeBuilder {

    MappedObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    boolean emulated = false;

    String remoteName;
    private String description;

    // FIXME: Extract to Json Specific Builder
    String protocolName;
    String jsonType;
    String openApiFormat;

    private ScimBuilder scim;

    public MappedBasicAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public MappedBasicAttributeBuilderImpl protocolName(String protocolName) {
        this.protocolName = protocolName;
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

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public MappedAttribute build() {
        return new MappedAttribute(this);
    }

    @Override
    public JsonMapping json() {
        return new JsonMapping() {
            @Override
            public JsonMapping type(String jsonType) {
                MappedBasicAttributeBuilderImpl.this.jsonType = jsonType;
                return this;
            }

            @Override
            public JsonMapping openApiFormat(String openapiFormat) {
                MappedBasicAttributeBuilderImpl.this.openApiFormat = openapiFormat;
                return this;
            }
        };
    }

    @Override
    public ConnIdMapping connId() {
        return new ConnIdMapping() {
            @Override
            public ConnIdMapping name(String name) {
                MappedBasicAttributeBuilderImpl.this.connIdBuilder.setName(name);
                return this;
            }

            @Override
            public ConnIdMapping type(Class<?> connIdType) {
                MappedBasicAttributeBuilderImpl.this.connIdBuilder.setType(connIdType);
                return this;
            }
        };
    }

    @Override
    public ScimMapping scim() {
        return (ScimBuilder) protocolMappings.computeIfAbsent(ScimAttributeMapping.class, m -> new ScimBuilder());
    }

    private class ScimBuilder implements AttributeProtocolMappingBuilder, ScimMapping {
        private String name;
        private String type;
        private AttributeMapping implementation;

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public ScimMapping name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public ScimMapping type(String name) {
            this.type = name;
            return this;
        }

        @Override
        public void implementation(AttributeMapping mapping) {
            this.implementation = mapping;
        }

        @Override
        public AttributeProtocolMapping<?> build() {
            if (implementation != null) {
                return new ScimAttributeMapping.Custom(name, type, implementation);
            }
            return new ScimAttributeMapping.Naive(name);
        }



        @Override
        public Class<?> suggestedConnIdType() {
            return null;
        }
    }

}
