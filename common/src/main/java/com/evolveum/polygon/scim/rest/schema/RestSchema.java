package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.Map;

public class RestSchema {

    private final Schema connIdSchema;
    private final Map<ObjectClass, MappedObjectClass> objectClasses;

    public RestSchema(Schema connIdSchema, Map<ObjectClass, MappedObjectClass> objectClasses) {
        this.connIdSchema = connIdSchema;
        this.objectClasses = objectClasses;
    }

    public Schema connIdSchema() {
        return connIdSchema;
    }

    public MappedObjectClass objectClass(String name) {
        return objectClasses.get(new ObjectClass(name));
    }
}
