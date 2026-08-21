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
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Connector;

import java.util.Map;

public class RestSchemaBuilderImpl extends BaseSchemaBuilder<
        RestSchemaBuilderImpl,
        RestObjectClassDefinitionBuilder,
        RestSchemaBuilder,
        RestObjectClassSchemaBuilder,
        RestObjectClassDefinition,
        RestSchema> implements RestSchemaBuilder {

    public RestSchemaBuilderImpl(Class<? extends Connector> connectorClass, ContextLookup context) {
        super(connectorClass, context);
    }

    @Override
    protected RestObjectClassDefinitionBuilder newObjectClass(DefinitionValue<String> name) {
        return new RestObjectClassDefinitionBuilder(this, name);
    }

    public Class<? extends Connector> connectorClass() {
        return connectorClass;
    }

    @Override
    public RestObjectClassDefinitionBuilder objectClass(String name) {
        return (RestObjectClassDefinitionBuilder) super.objectClass(name);
    }

    @Override
    public RestObjectClassDefinitionBuilder objectClass(String name, @DelegatesTo(RestObjectClassDefinitionBuilder.class) Closure<?> closure) {
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

    @Override
    protected RestSchema newSchema(Schema connIdSchema, Map<ObjectClass, RestObjectClassDefinition> objectClassMap) {
        return new RestSchema(connIdSchema, objectClassMap);
    }

    @Override
    protected void contributeAdditionalObjectClass(ObjectClassInfo info, Map<ObjectClass, RestObjectClassDefinition> objectClassMap) {
        // wrap in a mapping-less RestObjectClassDefinition so the handler framework can dispatch to it
        var mapped = new RestObjectClassDefinition(info, Map.of(), Map.of());
        objectClassMap.put(mapped.objectClass(), mapped);
    }
}
