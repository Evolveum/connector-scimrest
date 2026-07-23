/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestRelationshipBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSchemaBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.spi.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestSchemaBuilderImpl extends BaseSchemaBuilder<
        RestSchemaBuilderImpl,
        MappedObjectClassBuilder,
        RestSchemaBuilder,
        RestObjectClassSchemaBuilder> implements RestSchemaBuilder {

    private final List<ObjectClassInfo> additionalObjectClasses = new ArrayList<>();

    public RestSchemaBuilderImpl(Class<? extends Connector> connectorClass, ContextLookup context) {
        super(connectorClass, context);
    }

    @Override
    protected MappedObjectClassBuilder newObjectClass(DefinitionValue<String> name) {
        return new MappedObjectClassBuilder(this, name);
    }

    public Class<? extends Connector> connectorClass() {
        return connectorClass;
    }

    @Override
    public MappedObjectClassBuilder objectClass(String name) {
        return (MappedObjectClassBuilder) super.objectClass(name);
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
        var ret = new RelationshipBuilderImpl(name, this);
        return GroovyClosures.callAndReturnDelegate(closure, ret);
    }

    /**
     * Adds a ready-made ConnId object class (e.g. the shared conndev dev object classes defined in
     * {@code ConnDevSchema}) to the schema, alongside the mapped object classes.
     */
    public RestSchemaBuilderImpl defineObjectClass(ObjectClassInfo objectClass) {
        additionalObjectClasses.add(objectClass);
        return this;
    }

    @Override
    public RestSchema build() {
        if (objectClasses.isEmpty()) {
            initializeDummySchema();
        }

        var freshSchemaBuilder = new SchemaBuilder(connectorClass);
        Map<ObjectClass, MappedObjectClass> objectClassMap = new HashMap<>();
        for (var ocBuilder : objectClasses.values()) {
            var objectClassDef = ocBuilder.build();
            freshSchemaBuilder.defineObjectClass(objectClassDef.connId());
            objectClassMap.put(objectClassDef.objectClass(), objectClassDef);
        }
        for (var info : additionalObjectClasses) {
            freshSchemaBuilder.defineObjectClass(info);
            // wrap in a mapping-less MappedObjectClass so the handler framework can dispatch to it
            var mapped = new MappedObjectClass(info, Map.of(), Map.of());
            objectClassMap.put(mapped.objectClass(), mapped);
        }
        return new RestSchema(freshSchemaBuilder.build(), objectClassMap);
    }
}
