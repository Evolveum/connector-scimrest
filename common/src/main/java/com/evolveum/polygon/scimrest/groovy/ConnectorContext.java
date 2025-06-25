/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.*;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import com.evolveum.polygon.scimrest.impl.scim.ScimContext;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

public class ConnectorContext implements ContextLookup, RetrievableContext {

    Map<ObjectClass, ObjectClassHandler> handlers;
    BaseGroovyConnectorConfiguration configuration;

    private RestSchema schema;
    private RestContext rest;
    private ScimContext scim;

    public ConnectorContext(BaseGroovyConnectorConfiguration groovyConf) {
        this.configuration = groovyConf;
    }


    boolean isScimEnabled() {
        return scim != null;
    }



    public void schema(RestSchema build) {
        this.schema = build;
    }

    public void handlers(Map<ObjectClass, ObjectClassHandler> build) {
        this.handlers = build;
    }

    public ObjectClassHandler handlerFor(ObjectClass objectClass) {
        return this.handlers.get(objectClass);
    }

    public void rest(RestContext restContext) {
        this.rest = restContext;
    }

    public BaseGroovyConnectorConfiguration configuration() {
        return configuration;
    }

    public GroovyRestHandlerBuilder handlerBuilder(GroovyContext groovy) {
        return new GroovyRestHandlerBuilder(groovy, this);
    }

    public RestSchema schema() {
        return schema;
    }

    public RestContext rest() {
        return rest;
    }

    public ScimContext scim() {
        return scim;
    }

    public void initializeScim() {
        if (configuration instanceof ScimClientConfiguration scimConf) {
            scim = new ScimContext(this, scimConf);
        }
    }

    public void initializeRest(RestContext.AuthorizationCustomizer authorizationCustomizer) {
        if (configuration instanceof RestClientConfiguration restCfg) {
            rest = new RestContext(restCfg, authorizationCustomizer);
        }
    }

    @Override
    public <T extends RetrievableContext> T get(Class<T> contextType) throws IllegalStateException {
        var ret = getUnchecked(contextType);
        if (ret == null) {
            throw new IllegalStateException(String.format("No context found for type %s", contextType.getName()));
        }
        return ret;
    }

    private <T extends RetrievableContext> T getUnchecked(Class<T> contextType) {
        if (ConnectorContext.class.equals(contextType)) {
            return contextType.cast(this);
        }
        if (ScimContext.class.equals(contextType)) {
            return contextType.cast(scim);
        }
        if (RestContext.class.equals(contextType)) {
            return contextType.cast(rest);
        }
        return null;
    }
}
