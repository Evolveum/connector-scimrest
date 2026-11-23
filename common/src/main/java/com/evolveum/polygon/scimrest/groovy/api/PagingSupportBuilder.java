/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.scimrest.yaml.binding.PagingSupportHandler;

/**
Shared declarative paging configuration for REST collection readers. Each reader validates its supported tokens and request locations.
*/
public interface PagingSupportBuilder<Builder> {

    /**
    Sets the positive number of records requested per page.
    */
    Builder pageSize(int size);

    /**
    Maps a paging token to a query parameter, header, or body field supported by the reader.
    */
    Builder pagingParameter(String token, String location, String name);

    default Builder pagingParameter(String token, String location) {
        return pagingParameter(token, location, token);
    }

    /**
    Binds the existing YAML pagingSupport mapping through the shared handler.
    */
    @Yaml.Custom(PagingSupportHandler.class)
    default void pagingSupport() {
    }
}
