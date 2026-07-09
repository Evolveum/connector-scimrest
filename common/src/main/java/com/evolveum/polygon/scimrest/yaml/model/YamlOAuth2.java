/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

/** OAuth2 flow customization; each field is a Groovy block scalar. */
public class YamlOAuth2 {

    public String buildTokenRequest;
    public String parseTokenResponse;
    public String validateToken;
    public String applyToken;
    public String onResponse;
    public String implementation;
}
