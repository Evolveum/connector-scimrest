package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

public class RestAttributeBuilderImpl implements com.evolveum.polygon.scim.rest.groovy.api.RestAttributeBuilder {

    RestObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    boolean emulated = false;

    String remoteName;
    private String description;

    // FIXME: Extract to Json Specific Builder
    String protocolName;
    String jsonType;
    String openApiFormat;

    public RestAttributeBuilderImpl(RestObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public RestAttributeBuilderImpl protocolName(String protocolName) {
        this.protocolName = protocolName;
        return this;
    }

    @Override
    public RestAttributeBuilderImpl remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    @Override
    public void emulated(boolean emulated) {
        this.emulated = emulated;
    }

    @Override
    public RestAttributeBuilderImpl readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }


    @Override
    public RestAttributeBuilderImpl required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    @Override
    public RestAttributeBuilderImpl updateable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    @Override
    public RestAttributeBuilderImpl creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    public RestAttributeBuilderImpl description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public RestAttributeBuilderImpl returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return this;
    }

    @Override
    public RestAttributeBuilderImpl multiValued(boolean multiValued) {
        connIdBuilder.setMultiValued(multiValued);
        return this;
    }

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public RestAttribute build() {
        return new RestAttribute(this);
    }

    @Override
    public JsonMapping json() {
        return new JsonMapping() {
            @Override
            public JsonMapping type(String jsonType) {
                RestAttributeBuilderImpl.this.jsonType = jsonType;
                return this;
            }

            @Override
            public JsonMapping openApiFormat(String openapiFormat) {
                RestAttributeBuilderImpl.this.openApiFormat = openapiFormat;
                return this;
            }
        };
    }

    @Override
    public ConnIdMapping connId() {
                return new ConnIdMapping() {
            @Override
            public ConnIdMapping name(String name) {
                RestAttributeBuilderImpl.this.connIdBuilder.setName(name);
                return this;
            }
        };
    }

    @Override
    public ScimMapping scim() {
        throw new UnsupportedOperationException();
    }


}
