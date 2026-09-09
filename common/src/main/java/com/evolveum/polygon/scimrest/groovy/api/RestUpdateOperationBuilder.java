/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimUpdateBuilder;
import com.evolveum.polygon.scimrest.yaml.binding.SupportedAttributesHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeDelta;

import java.util.Set;

public interface RestUpdateOperationBuilder extends RestObjectOperationBuilder<RestUpdateOperationBuilder.Endpoint>, UpdateOperationBuilder {

    ScimUpdateBuilder scim();

    default ScimUpdateBuilder scim(@DelegatesTo(value = ScimUpdateBuilder.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, scim());
    }


    @Override
    Endpoint endpoint(HttpMethod method, String path);

    @Override
    default Endpoint endpoint(String path) {
        return endpoint(HttpMethod.PUT, path);
    }

    @Override
    default Endpoint endpoint(String path,
                              @DelegatesTo(value = EndpointBuilder.SingleObject.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        return endpoint(PUT, path, value);
    }

    @Override
    default Endpoint endpoint(HttpMethod method, String path,
                              @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        var endpoint = endpoint(method, path);
        return GroovyClosures.callAndReturnDelegate(value, endpoint);
    }

    interface Endpoint extends UpdateOperationBuilder.AttributeSpecific<UpdateOperationBuilder.AttributeValueFilter, Endpoint>,
            EndpointBuilder.SingleObject<UpdateOperationBuilder.UpdateRequest, Set<AttributeDelta>> {

        /**
         * Marker for the YAML front-end: the {@code supportedAttributes:} block is bound by
         * {@link SupportedAttributesHandler}; the method body is unused.
         */
        @Yaml.Custom(SupportedAttributesHandler.class)
        default void supportedAttributes() {
        }
    }
}
