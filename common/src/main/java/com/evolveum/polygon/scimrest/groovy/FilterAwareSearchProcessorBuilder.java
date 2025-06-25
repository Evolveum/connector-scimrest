/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

public interface FilterAwareSearchProcessorBuilder {

    boolean isEnabled();

    boolean emptyFilterSupported();

    default boolean anyFilterSupported() {
        return false;
    }

    FilterAwareExecuteQueryProcessor build();
}
