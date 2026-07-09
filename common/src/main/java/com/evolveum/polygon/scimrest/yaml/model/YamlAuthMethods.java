/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

import java.util.List;

/** Authentication methods of one client (REST or SCIM share the same shape). */
public class YamlAuthMethods {

    public YamlAuthMethod apiKey;
    public YamlAuthMethod basic;
    public YamlAuthMethod bearer;
    public YamlAuthMethod jwtBearer;
    public YamlOAuth2 oauth2ClientCredentials;
    public YamlOAuth2 oauth2JwtBearer;
    public YamlOAuth2 oauth2Password;
    public YamlOAuth2 oauth2Saml;
    public List<String> preference;
}
