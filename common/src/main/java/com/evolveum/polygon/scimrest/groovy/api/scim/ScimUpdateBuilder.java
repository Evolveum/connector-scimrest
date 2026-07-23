package com.evolveum.polygon.scimrest.groovy.api.scim;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ScimUpdateBuilder extends ScimOperationBuilder<ScimOperationBuilder.Limitations, ScimUpdateBuilder> {

    Put put();

    default Put put(@DelegatesTo(value = Put.class, strategy = Closure.DELEGATE_ONLY)
                        @Script.Initialization
                        Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, put());
    }

    Patch patch();

    default Patch patch(@DelegatesTo(value = Patch.class, strategy = Closure.DELEGATE_ONLY)
                            @Script.Initialization
                            Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, patch());
    }

    interface Put extends AttributeSpecific<AttributeValueFilter, Put> {

        @Override
        default AttributeValueFilter supportedAttribute(String attributeName,
                                                        @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                                                        @Script.Initialization
                                                        Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, supportedAttribute(attributeName));
        }

    }

    interface Patch extends AttributeSpecific<AttributeValueFilter, Patch> {

        @Override
        default AttributeValueFilter supportedAttribute(String attributeName,
                                                @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                                                    @Script.Initialization
                                                    Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, supportedAttribute(attributeName));
        }

    }

    interface AttributeValueFilter extends ScimOperationBuilder.AttributeValueFilter<ScimUpdateBuilder.AttributeLimitations> {

        AttributeLimitations limitations(@DelegatesTo(value = AttributeLimitations.class, strategy = Closure.DELEGATE_ONLY)
                                             @Script.Initialization
                                             Closure<?> definition);
    }

    interface AttributeLimitations extends ScimOperationBuilder.AttributeLimitations {

        // FIXME: This could be SCIM operation enum
        String ADD = "add";
        String REPLACE = "replace";
        String REMOVE = "remove";

        AttributeLimitations operations(String... operations);
    }
}