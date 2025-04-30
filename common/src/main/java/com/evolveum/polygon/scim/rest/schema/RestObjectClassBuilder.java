package com.evolveum.polygon.scim.rest.schema;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class RestObjectClassBuilder {

    private final String name;
    private ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();
    Map<String, RestAttributeBuilder> nativeAttributes = new HashMap<>();

    public RestObjectClassBuilder(RestSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
        connIdBuilder.setType(name);
    }

    public RestAttributeBuilder attribute(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> new RestAttributeBuilder(RestObjectClassBuilder.this, k));
        return builder;
    }

    public RestAttributeBuilder attribute(String name, Closure closure) {
        var attr = attribute(name);
        closure.setDelegate(attr);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return attr;
    }

    public RestObjectClass build() {
        var connIdAttrs = new HashMap<String, RestAttribute>();
        var nativeAttrs = new HashMap<String, RestAttribute>();
        for (var attrBuilder : nativeAttributes.values()) {
            var attribute = attrBuilder.build();
            connIdBuilder.addAttributeInfo(attribute.connId());
            connIdAttrs.put(attribute.connId().getName(), attribute);
            nativeAttrs.put(attribute.remoteName(), attribute);
        }

        return new RestObjectClass(connIdBuilder.build(), nativeAttrs, connIdAttrs);
    }
}
