/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

public interface AttributeResolutionContext extends BaseScriptContext {

    default ConnectorObjectBuilder getValue() {
        return value();
    }

    ConnectorObjectBuilder value();

}
