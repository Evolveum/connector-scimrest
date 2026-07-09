/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

/**
 * Filter supported by a search endpoint: {@code spec} is a Groovy expression evaluating to a
 * {@code FilterSpecification}, {@code request} a Groovy block scalar mapping the matched filter
 * onto the request. The conndev {@code YamlSupportedFilter} (spec only) belongs to the generic
 * {@code custom} section and is a different construct.
 */
public class YamlEndpointFilter {

    public String spec;
    public String request;
}
