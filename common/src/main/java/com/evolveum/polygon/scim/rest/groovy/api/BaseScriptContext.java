/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;

public interface BaseScriptContext {


    /**
     * Returns the object class definition for the current search operation.
     *
     * @return the object class definition
     */
    MappedObjectClass definition();


    /**
     * Creates a filter builder for the specified attribute using its protocol name.
     * This method is used to create filters for attributes in custom search implementations.
     *
     * @param protocolName the protocol name of the attribute to filter on
     * @return a filter builder for the specified attribute
     * @throws IllegalArgumentException if the attribute is not found
     */
    default FilterBuilder.AttributeFilterBuilder attributeFilter(String protocolName) {
        var attribute = definition().attributeFromProtocolName(protocolName);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute: " + protocolName);
        }

        return FilterBuilder.forAttribute(attribute.connId().getName());
    }

    /**
     * Returns an object class handler for the specified object class name.
     * This allows access to other object classes during a custom search implementation.
     *
     * @param name the name of the object class
     * @return an object class handler for the specified object class
     */
    ObjectClassScripting objectClass(String name);

}
