package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.common.security.GuardedString;

public interface AuthenticationCustomizationBuilder {

    RestBuilder rest(@DelegatesTo(RestBuilder.class) Closure<?> closure);


    interface RestBuilder {

        RestContext.AuthorizationCustomizer customizer(Class<? extends RestClientConfiguration> type, @DelegatesTo(CustomizationContext.class) Closure<?> o);


        default RestContext.AuthorizationCustomizer basic(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.BasicAuthorization.class, o);
        }

        default RestContext.AuthorizationCustomizer tokenBased(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.TokenAuthorization.class, o);
        }

        default RestContext.AuthorizationCustomizer apiKey(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.ApiKeyAuthorization.class, o);
        }

    }

    interface CustomizationContext {

        RestClientConfiguration getConfiguration();

        RestContext.RequestBuilder getRequest();

        default String decrypt(GuardedString value) {
            var accessor = new GuardedStringAccessor();
            value.access(accessor);
            return accessor.getClearString();
        }
    }
}
