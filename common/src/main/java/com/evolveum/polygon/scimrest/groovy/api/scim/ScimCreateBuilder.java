package com.evolveum.polygon.scimrest.groovy.api.scim;


import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.Script;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ScimCreateBuilder extends
        ScimOperationBuilder<ScimOperationBuilder.Limitations, ScimCreateBuilder>,
        RestUpdateOperationBuilder.AttributeSpecific<ScimOperationBuilder.AttributeValueFilter<ScimOperationBuilder.AttributeLimitations>, ScimCreateBuilder> {

    @Override
    default AttributeValueFilter<AttributeLimitations> supportedAttribute(String attributeName,
                                                                          @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                                                                          @Script.Initialization
                                                                          Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, supportedAttribute(attributeName));
    }
}
