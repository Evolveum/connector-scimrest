/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.groovy.impl;

public class DevelopmentKitConfiguration extends ReadOnlyConfiguration {

    String testConnectionUrl;

    String[] schemaScripts;

    String[] operationScripts;

    public String getTestConnectionUrl() {
        return testConnectionUrl;
    }

    public void setTestConnectionUrl(String testConnectionUrl) {
        this.testConnectionUrl = testConnectionUrl;
    }

    public String[] getOperationScripts() {
        return operationScripts;
    }

    public void setOperationScripts(String[] operationScripts) {
        this.operationScripts = operationScripts;
    }

    public String[] getSchemaScripts() {
        return schemaScripts;
    }

    public void setSchemaScripts(String[] schemaScripts) {
        this.schemaScripts = schemaScripts;
    }
}
