/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;


/**
 * One operations YAML document: the REST/SCIM-specific counterpart of one Groovy operation or
 * authentication script. Following the Groovy file-per-operation convention
 * ({@code User.search.groovy}, {@code authorization.op.groovy}), a document carries exactly one of
 * the sections; the operation sections require {@code objectClass}.
 */
public class YamlOperationDocument {

    public String objectClass;
    public YamlSearch search;
    public YamlOperation create;
    public YamlOperation update;
    public YamlAuthentication authentication;
}
