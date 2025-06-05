package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

public class MappedAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestAttributeBuilder {

    MappedObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    boolean emulated = false;

    String remoteName;
    private String description;

    // FIXME: Extract to Json Specific Builder
    String protocolName;
    String jsonType;
    String openApiFormat;

    private ScimBuilder scim;

    public MappedAttributeBuilderImpl(MappedObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public MappedAttributeBuilderImpl protocolName(String protocolName) {
        this.protocolName = protocolName;
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    @Override
    public void emulated(boolean emulated) {
        this.emulated = emulated;
    }

    @Override
    public MappedAttributeBuilderImpl readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }


    @Override
    public MappedAttributeBuilderImpl required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl updateable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    public MappedAttributeBuilderImpl description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return this;
    }

    @Override
    public MappedAttributeBuilderImpl multiValued(boolean multiValued) {
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
                MappedAttributeBuilderImpl.this.jsonType = jsonType;
                return this;
            }

            @Override
            public JsonMapping openApiFormat(String openapiFormat) {
                MappedAttributeBuilderImpl.this.openApiFormat = openapiFormat;
                return this;
            }
        };
    }

    @Override
    public ConnIdMapping connId() {
                return new ConnIdMapping() {
            @Override
            public ConnIdMapping name(String name) {
                MappedAttributeBuilderImpl.this.connIdBuilder.setName(name);
                return this;
            }
        };
    }

    @Override
    public ScimMapping scim() {
        if (this.scim == null) {
            this.scim = new ScimBuilder();
        }
        return scim;
    }

    private class ScimBuilder implements ScimMapping {
        private String name;

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public ScimMapping name(String name) {
            this.name = name;
            return this;
        }
    }

}
