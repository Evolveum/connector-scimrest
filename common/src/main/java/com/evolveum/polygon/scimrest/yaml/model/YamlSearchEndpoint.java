/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.model;

import java.util.List;

public class YamlSearchEndpoint {

    public String path;
    public String responseFormat;
    public Boolean emptyFilterSupported;
    /** Groovy block scalar extracting the object list from the response. */
    public String objectExtractor;
    public String pagingSupport;
    public Boolean singleResult;
    public List<YamlEndpointFilter> supportedFilters;
}
