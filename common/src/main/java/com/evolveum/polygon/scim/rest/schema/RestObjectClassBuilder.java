package com.evolveum.polygon.scim.rest.schema;

import groovy.lang.Closure;

import java.util.HashMap;
import java.util.Map;

public class RestObjectClassBuilder {

    private final String name;
    Map<String, RestAttributeBuilder> nativeAttributes = new HashMap<>();

    public RestObjectClassBuilder(RestSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
    }

    RestAttributeBuilder attribute(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> new RestAttributeBuilder(RestObjectClassBuilder.this, k));
        return builder;
    }

    RestAttributeBuilder attribute(String name, Closure closure) {
        var attr = attribute(name);
        closure.setDelegate(attr);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return attr;
    }
}
