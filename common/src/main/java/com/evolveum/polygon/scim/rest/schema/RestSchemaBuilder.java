package com.evolveum.polygon.scim.rest.schema;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class RestSchemaBuilder {

    Map<String, RestObjectClassBuilder> objectClasses = new HashMap<>();

    public RestObjectClassBuilder objectClass(String name) {
        return objectClasses.computeIfAbsent("name", k -> new RestObjectClassBuilder(RestSchemaBuilder.this, k));
    }

    public RestObjectClassBuilder objectClass(String name, @DelegatesTo(RestObjectClassBuilder.class) Closure<?> closure) {
        var objectClass = objectClass(name);
        closure.setDelegate(objectClass);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return objectClass;
    }

    public RestSchema build() {
        return null;
    }
}
