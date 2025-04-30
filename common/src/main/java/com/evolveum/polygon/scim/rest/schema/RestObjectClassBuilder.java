package com.evolveum.polygon.scim.rest.schema;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class RestObjectClassBuilder {

    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }


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

    public RestObjectClassBuilder connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = nativeAttributes.get(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connIdBuilder.setName(finalName);
        return this;
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
