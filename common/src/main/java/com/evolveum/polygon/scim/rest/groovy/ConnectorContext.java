package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.ScimContext;
import com.evolveum.polygon.scim.rest.config.RestClientConfiguration;
import com.evolveum.polygon.scim.rest.config.ScimClientConfiguration;
import com.evolveum.polygon.scim.rest.schema.RestSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

public class ConnectorContext {

    Map<ObjectClass, ObjectClassHandler<RestContext>> handlers;
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

    public void handlers(Map<ObjectClass, ObjectClassHandler<RestContext>> build) {
        this.handlers = build;
    }

    public ObjectClassHandler<RestContext> handlerFor(ObjectClass objectClass) {
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
            scim = new ScimContext(scimConf);
        }
    }

    public void initializeRest(RestContext.AuthorizationCustomizer authorizationCustomizer) {
        if (configuration instanceof RestClientConfiguration restCfg) {
            rest = new RestContext(restCfg, authorizationCustomizer);
        }
    }
}
