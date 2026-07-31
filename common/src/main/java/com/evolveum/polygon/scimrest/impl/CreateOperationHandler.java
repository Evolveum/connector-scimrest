/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.scimrest.impl;

import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

public interface CreateOperationHandler extends AttributeAwareOperationHandler<Attribute, CreateOperationHandler> {

     Result create(final Set<Attribute> createAttributes,
                           final OperationOptions options);

     record Result(ObjectClass cls, Uid uid, ConnectorObject object) {

     }
}
