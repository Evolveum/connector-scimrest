package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.common.security.GuardedString;

public interface AuthenticationCustomizationBuilder {

    RestBuilder rest(@DelegatesTo(RestBuilder.class) @Script.Initialization Closure<?> closure);

    ScimBuilder scim(@DelegatesTo(ScimBuilder.class) @Script.Initialization Closure<?> closure);

    interface RestBuilder {

        AuthorizationCustomizer<RestClientConfiguration> customizer(Class<? extends RestClientConfiguration> type, @DelegatesTo(CustomizationContext.class) @Script.Initialization Closure<?> o);


        default AuthorizationCustomizer<RestClientConfiguration> basic(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.BasicAuthorization.class, o);
        }

        default AuthorizationCustomizer<RestClientConfiguration> tokenBased(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.TokenAuthorization.class, o);
        }

        default AuthorizationCustomizer<RestClientConfiguration> apiKey(@DelegatesTo(value = CustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(RestClientConfiguration.ApiKeyAuthorization.class, o);
        }

        void oauth2(@DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                    @Script.Initialization Closure<?> o);
    }

    interface OAuth2Builder {
        OAuth2Builder buildTokenRequest(@Script.Runtime Closure<?> hook);
        OAuth2Builder parseTokenResponse(@Script.Runtime Closure<?> hook);
        OAuth2Builder validateToken(@Script.Runtime Closure<?> hook);
        OAuth2Builder applyToken(@Script.Runtime Closure<?> hook);
    }

    interface ScimBuilder {

        AuthorizationCustomizer<ScimClientConfiguration> customizer(Class<? extends ScimClientConfiguration> type,
                @DelegatesTo(ScimCustomizationContext.class) @Script.Initialization Closure<?> o);

        default AuthorizationCustomizer<ScimClientConfiguration> bearer(
                @DelegatesTo(value = ScimCustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(ScimClientConfiguration.BearerToken.class, o);
        }

        default AuthorizationCustomizer<ScimClientConfiguration> httpBasic(
                @DelegatesTo(value = ScimCustomizationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<?> o) {
            return customizer(ScimClientConfiguration.HttpBasic.class, o);
        }
    }

    interface CustomizationContext {

        RestClientConfiguration getConfiguration();

        HttpRequestDTO getRequest();

        default String decrypt(GuardedString value) {
            var accessor = new GuardedStringAccessor();
            value.access(accessor);
            return accessor.getClearString();
        }
    }

    interface ScimCustomizationContext {

        ScimClientConfiguration getConfiguration();

        HttpRequestDTO getRequest();

        default String decrypt(GuardedString value) {
            var accessor = new GuardedStringAccessor();
            value.access(accessor);
            return accessor.getClearString();
        }
    }
}
