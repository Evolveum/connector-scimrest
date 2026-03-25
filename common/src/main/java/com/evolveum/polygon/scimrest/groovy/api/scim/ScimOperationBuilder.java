/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api.scim;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.Script;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ScimOperationBuilder<L, B extends ScimOperationBuilder<L,B>> {

    B enabled(boolean enabled);

    boolean isEnabled();

    /**
     * Allows to set and configure limitations and quirks of remote SCIM server
     *
     * The connector framework will use this configuration correctly
     *
     * @return List of limitations
     */
    L limitations();


    default L limitations(@DelegatesTo(value = Limitations.class, strategy = Closure.DELEGATE_ONLY)
                  @Script.Initialization
                  Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, limitations());
    }

    interface Limitations {

    }


    interface AttributeSpecific<A extends AttributeValueFilter<?>, T extends AttributeSpecific<A,T>> extends RestUpdateOperationBuilder.AttributeSpecific<A, T> {


        @Override
        default A supportedAttribute(String attributeName,
                             @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                             @Script.Initialization
                             Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, supportedAttribute(attributeName));
        }

    }

    interface AttributeValueFilter<T extends AttributeLimitations> extends RestUpdateOperationBuilder.AttributeValueFilter {

        T limitations();

        default AttributeLimitations limitations(@DelegatesTo(value = AttributeLimitations.class, strategy = Closure.DELEGATE_ONLY)
                                                 @Script.Initialization
                                                 Closure<?> definition) {
            return GroovyClosures.callAndReturnDelegate(definition, limitations());
        }
    }


    interface AttributeLimitations {

        /**
         * Maximum of specific attribute operations allowed per single request
         *
         * If the number of specific attribute operations exceeds the limit, they will be executed in subsequent follow-up
         * request
         *
         * @param maximum Maximum of supported operations per single request
         * @return this duilder
         */
        ScimUpdateBuilder.AttributeLimitations maxPerRequest(int maximum);

    }
}
