/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.Map;

public class RestSchema extends BaseSchema<MappedObjectClass> {

    public RestSchema(Schema connIdSchema, Map<ObjectClass, MappedObjectClass> objectClasses) {
        super(connIdSchema, objectClasses);
    }
}
