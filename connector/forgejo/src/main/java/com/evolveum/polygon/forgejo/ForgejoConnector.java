package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.*;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "forgejo.rest.display", configurationClass = ForgejoConfiguration.class)
public class ForgejoConnector extends AbstractGroovyRestConnector<ForgejoConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/User.native.schema.groovy");
        loader.loadFromResource("/User.connid.schema.groovy");
        loader.loadFromResource("/Organization.native.schema.groovy");
        loader.loadFromResource("/Organization.connid.schema.groovy");
        loader.loadFromResource("/Team.native.schema.groovy");
        loader.loadFromResource("/Team.connid.schema.groovy");
        loader.loadFromResource("/associations.schema.groovy");


    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        builder.loadFromResource("/User.search.groovy");
        builder.loadFromResource("/Organization.search.groovy");
        builder.loadFromResource("/Team.search.groovy");
        builder.loadFromResource("/Team.custom.minimal.search.groovy");
    }

    @Override
    protected RestContext.AuthorizationCustomizer authorizationCustomizer() {
        return (c,request) -> {
            if (c instanceof HttpClientConfiguration.TokenAuthorization tokenAuth) {
                var tokenAccessor = new GuardedStringAccessor();
                tokenAuth.getAuthorizationTokenValue().access(tokenAccessor);
                request.header("Authorization", "token " + tokenAccessor.getClearString());
            }
        };
    }
}
