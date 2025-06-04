package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.spi.Connector;

import java.util.HashMap;
import java.util.Map;

public class RestSchemaBuilder implements com.evolveum.polygon.scim.rest.groovy.api.SchemaBuilder {

    private final SchemaBuilder schemaBuilder;
    private final Map<String, RestObjectClassBuilder> objectClasses = new HashMap<>();

    public RestSchemaBuilder(Class<? extends Connector> connectorClass) {
        this.schemaBuilder = new SchemaBuilder(connectorClass);
    }

    @Override
    public RestObjectClassBuilder objectClass(String name) {
        return objectClasses.computeIfAbsent(name, k -> new RestObjectClassBuilder(RestSchemaBuilder.this, k));
    }

    @Override
    public RestObjectClassBuilder objectClass(String name, @DelegatesTo(RestObjectClassBuilder.class) Closure<?> closure) {
        var objectClass = objectClass(name);
        closure.setDelegate(objectClass);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return objectClass;
    }

    @Override
    public RestRelationshipBuilder relationship(String name, @DelegatesTo(RestRelationshipBuilder.class) Closure<?> closure) {
        throw new UnsupportedOperationException();
    }

    public RestSchema build() {
        Map<ObjectClass, RestObjectClass> objectClassMap = new HashMap<>();
        for (var ocBuilder : objectClasses.values()) {
            var objectClassDef = ocBuilder.build();
            schemaBuilder.defineObjectClass(objectClassDef.connId());
            objectClassMap.put(objectClassDef.objectClass(), objectClassDef);
        }
        return new RestSchema(schemaBuilder.build(), objectClassMap);
    }

    public Iterable<RestObjectClassBuilder> allObjectClasses() {
        return objectClasses.values();
    }
}
