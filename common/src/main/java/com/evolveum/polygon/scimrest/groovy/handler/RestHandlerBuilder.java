/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.handler;

import com.evolveum.polygon.scimrest.groovy.schema.BaseOperationSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.auth.AuthorizationCustomizationBuilderImpl;
import com.evolveum.polygon.scimrest.groovy.connector.RestConnectorContext;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.ObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.AbstractOperationSupportBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.OperationBuilder;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class RestHandlerBuilder extends AbstractOperationSupportBuilder<RestHandlerBuilder, BaseOperationSupportBuilder>
        implements OperationBuilder {

    private final RestConnectorContext context;

    AuthorizationCustomizationBuilderImpl authorization = new AuthorizationCustomizationBuilderImpl();

    public RestHandlerBuilder(RestConnectorContext context) {
        super(context);
        this.context = context;
    }

    @Override
    protected BaseOperationSupportBuilder newObjectSpecific(BaseObjectClassDefinition classDefinition) {
        return new BaseOperationSupportBuilder(context, (RestObjectClassDefinition) classDefinition);
    }

    // Disambiguates the identical default objectClass(String, Closure) inherited from both
    // OperationBuilder and (via AbstractOperationSupportBuilder) OperationSupportBuilder — Java
    // treats them as unrelated defaults even though both just delegate to objectClass(className).
    @Override
    public BaseOperationSupportBuilder objectClass(
            String className,
            @DelegatesTo(value = ObjectOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY)
            @Script.Initialization
            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(className));
    }

    @Override
    public AuthenticationCustomizationBuilder authentication(@Script.Initialization Closure<?> o) {
        return GroovyClosures.callAndReturnDelegate(o, authentication());
    }

    /** Closure-free entry point for non-Groovy front-ends (e.g. YAML). */
    public AuthenticationCustomizationBuilder authentication() {
        return authorization;
    }

    public AuthorizationCustomizer<RestClientConfiguration> restCustomizer() {
        return authorization.restCustomizer();
    }

    public AuthorizationCustomizer<ScimClientConfiguration> scimCustomizer() {
        return authorization.scimCustomizer();
    }
}
