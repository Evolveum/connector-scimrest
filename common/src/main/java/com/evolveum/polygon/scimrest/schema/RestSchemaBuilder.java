/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.HashMap;
import java.util.Map;

public class RestSchemaBuilder implements com.evolveum.polygon.scimrest.groovy.api.SchemaBuilder {

    private final SchemaBuilder schemaBuilder;
    private final Map<String, MappedObjectClassBuilder> objectClasses = new HashMap<>();

    public RestSchemaBuilder(Class<? extends Connector> connectorClass) {
        this.schemaBuilder = new SchemaBuilder(connectorClass);
    }

    @Override
    public MappedObjectClassBuilder objectClass(String name) {
        return objectClasses.computeIfAbsent(name, k -> new MappedObjectClassBuilder(RestSchemaBuilder.this, k));
    }

    @Override
    public MappedObjectClassBuilder objectClass(String name, @DelegatesTo(MappedObjectClassBuilder.class) Closure<?> closure) {
        var objectClass = objectClass(name);
        closure.setDelegate(objectClass);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return objectClass;
    }

    @Override
    public RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) Closure<?> closure) {
        var ret =  new RelationshipBuilderImpl(name, this);
        return GroovyClosures.callAndReturnDelegate(closure, ret);
    }

    public RestSchema build() {
        if (objectClasses.isEmpty()) {
            initializeDummySchema();
        }

        Map<ObjectClass, MappedObjectClass> objectClassMap = new HashMap<>();
        for (var ocBuilder : objectClasses.values()) {
            var objectClassDef = ocBuilder.build();
            schemaBuilder.defineObjectClass(objectClassDef.connId());
            objectClassMap.put(objectClassDef.objectClass(), objectClassDef);
        }
        return new RestSchema(schemaBuilder.build(), objectClassMap);
    }

    /**
     * This is workaround for state in connector development (and MidPoint), which prevents issuing test connection
     * without any object class
     */
    private void initializeDummySchema() {
        var oc = objectClass("__Dummy");
        oc.attribute("id").connId().name(Uid.NAME).type(String.class);
        oc.attribute("name").connId().name(Name.NAME).type(String.class);
    }

    public Iterable<MappedObjectClassBuilder> allObjectClasses() {
        return objectClasses.values();
    }
}
