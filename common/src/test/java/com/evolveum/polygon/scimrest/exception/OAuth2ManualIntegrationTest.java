/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

// This test is intended for local development only — all test methods have enabled = false.
// Run individual tests directly from IDE against a local mock server or Hydra instance.
// Do not enable these tests in CI.
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;

/**
 * Manuálny integračný test pre OAuth2 — spúšťaj priamo z IDE.
 *
 * Nastav konštanty nižšie podľa svojho mock servera a spusti jeden z testov.
 * V mock serveri uvidíš presne, čo connector posiela.
 *
 * testOAuth2Flow()         — štandardný flow, bez Groovy skriptu (Part A konfigurácia)
 * testOAuth2FlowWithScript — rovnaký flow, ale cez Groovy skript (Part B hooks)
 */
public class OAuth2ManualIntegrationTest {

    // --- Uprav podľa svojho mock servera ---
    private static final String BASE_URL       = "http://localhost:3000";
    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String TEST_ENDPOINT  = "/api/auth/verify";
    private static final String CLIENT_ID      = "your-client-id";
    private static final String CLIENT_SECRET  = "your-client-secret";
    private static final String SCOPE          = null;
    // ---------------------------------------

    /**
     * Štandardný flow — autorizácia riadená len konfiguráciou (Part A).
     * Groovy skript nie je potrebný.
     */
    @Test(enabled = false)
    public void testOAuth2Flow() {
        var config = new ManualTestConfig();
        var connector = buildConnector(config, null);

        System.out.println(">>> Volám connector.test() — pozri mock server čo prichádza...");
        connector.test();
        System.out.println(">>> Hotovo.");
    }

    /**
     * Flow s Groovy skriptom (Part B hooks).
     * Ukazuje ako pridať extra param, spracovať vnorený response a použiť vlastný header.
     * Uprav skript nižšie podľa toho, čo tvoj mock server očakáva / vracia.
     */
    /**
     * Simuluje refresh token flow v jedinom connector.test() volaní.
     *
     * tokenValidation predvyplní context s expirovaným access_token a platným
     * refresh_token, potom vyhodnotí expires_at štandardným spôsobom — vráti false.
     * tokenRequest následne použije grant_type=refresh_token.
     *
     * Mock server musí na /oauth/token akceptovať grant_type=refresh_token
     * a vrátiť nový access_token.
     */
    @Test(enabled = false)
    public void testOAuth2FlowWithScript() {
        var script = """
                authentication {
                    rest {
                        oauth2 { oauth2Context ->
                            validateToken {
                                println "[validateToken] tokenEndpoint: ${configuration.tokenUrl}"
                                if (!get("access_token")) {
                                    println "[validateToken] žiadny token — predvypĺňam expirovaný token + refresh_token"
                                    set("access_token",  "mocked-expired-token")
                                    set("expires_at",    java.time.Instant.now().minusSeconds(300))
                                    set("refresh_token", "mocked-refresh-token")
                                    return false
                                }
                                def expiresAt = get("expires_at")
                                def valid = expiresAt != null && expiresAt.isAfter(java.time.Instant.now())
                                println "[validateToken] token platný: ${valid} (expires_at=${expiresAt})"
                                valid
                            }

                            buildTokenRequest { request ->
                                println "[buildTokenRequest] clientId: ${configuration.clientId}"
                                println "[buildTokenRequest] refresh_token: ${get('refresh_token')}"
                                if (get("refresh_token")) {
                                    request.formParam("grant_type",    "refresh_token")
                                    request.formParam("refresh_token", get("refresh_token"))
                                    println "[buildTokenRequest] grant_type=refresh_token nastavený"
                                }
                            }

                            parseTokenResponse { response ->
                                println "[parseTokenResponse] odpoveď servera: ${response}"
                                set("access_token",  response.access_token)
                                set("expires_in",    response.expires_in ?: 3600)
                                if (response.refresh_token) {
                                    set("refresh_token", response.refresh_token)
                                }
                                println "[parseTokenResponse] access_token uložený: ${get('access_token')}"
                            }

                            applyToken { request ->
                                println "[applyToken] endpoint: ${configuration.baseAddress}"
                                request.header("Authorization", "Bearer ${get('access_token')}")
                            }
                        }
                    }
                }
                """;

        var config = new ManualTestConfig();
        var connector = buildConnector(config, script);

        System.out.println(">>> Volám connector.test() — očakávaj grant_type=refresh_token na mock serveri");
        connector.test();
        System.out.println(">>> Hotovo.");
    }

    // -------------------------------------------------------------------------

    private static AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> buildConnector(
            ManualTestConfig config, String groovyScript) {
        var connector = new ManualTestConnector(groovyScript);
        connector.init(config);
        return connector;
    }

    // --- konfigurácia ---

    private static class ManualTestConfig extends BaseRestGroovyConnectorConfiguration
            implements RestClientConfiguration.OAuth2Authorization {

        @Override public String getBaseAddress()          { return BASE_URL; }
        @Override public String getRestTestEndpoint()     { return TEST_ENDPOINT; }
        @Override public String getRestOAuth2TokenUrl()    { return BASE_URL + TOKEN_ENDPOINT; }
        @Override public String getRestOAuth2ClientId()   { return CLIENT_ID; }
        @Override public GuardedString getRestOAuth2ClientSecret() {
            return new GuardedString(CLIENT_SECRET.toCharArray());
        }

        @Override public GuardedString getRestOAuth2PrivateKey() {
            return null;
        }
    }

    // --- connector stub ---

    private static class ManualTestConnector
            extends AbstractGroovyRestConnector<BaseRestGroovyConnectorConfiguration> {

        private final String groovyScript;

        ManualTestConnector(String groovyScript) {
            super(false);
            this.groovyScript = groovyScript;
        }

        @Override protected void initializeSchema(GroovySchemaLoader loader) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            if (groovyScript != null) {
                builder.loadFromString(groovyScript);
            }
        }
    }
}
