package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.groovy.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

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
    private String description;

    public RestObjectClassBuilder(RestSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
        connIdBuilder.setType(name);
    }

    public RestAttributeBuilder attribute(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> new RestAttributeBuilder(RestObjectClassBuilder.this, k));
        return builder;
    }

    public RestReferenceAttributeBuilder reference(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> new RestReferenceAttributeBuilder(RestObjectClassBuilder.this, k));
        return (RestReferenceAttributeBuilder) builder;
    }

    public RestAttributeBuilder attribute(String name, @DelegatesTo(RestAttributeBuilder.class) Closure closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    public RestReferenceAttributeBuilder reference(String name, @DelegatesTo(RestReferenceAttributeBuilder.class) Closure closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    public RestObjectClassBuilder description(String description) {
        this.description = description;
        return this;
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
