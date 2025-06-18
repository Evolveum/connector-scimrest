package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.groovy.GroovyClosures;
import com.evolveum.polygon.scim.rest.groovy.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.RestReferenceAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

public class MappedObjectClassBuilder implements ObjectClassSchemaBuilder {

    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }

    private final String name;
    private final ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();
    Map<String, MappedAttributeBuilderImpl> nativeAttributes = new HashMap<>();
    private String description;
    private ScimMapping scim;

    public MappedObjectClassBuilder(RestSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
        connIdBuilder.setType(name);
    }

    @Override
    public MappedAttributeBuilderImpl attribute(String name) {
        return nativeAttributes.computeIfAbsent(name, (k) -> new MappedAttributeBuilderImpl(this, k));
    }

    @Override
    public MappedAttributeBuilderImpl reference(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> {
            var ret = new MappedAttributeBuilderImpl(MappedObjectClassBuilder.this, k);
            ret.connIdBuilder.setType(ConnectorObjectReference.class);
            return ret;
        });
        return (MappedAttributeBuilderImpl) builder;
    }


    @Override
    public MappedBasicAttributeBuilderImpl attribute(String name, @DelegatesTo(RestAttributeBuilder.class) Closure closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public MappedAttributeBuilderImpl reference(String name, @DelegatesTo(RestReferenceAttributeBuilder.class) Closure closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public MappedObjectClassBuilder description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            this.scim = new ScimBuilder();
            scim.name(name);
        }
        return this.scim;
        
    }

    public String name() {
        return name;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(ScimMapping.class)  Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    public MappedObjectClassBuilder connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = attribute(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connId().name(finalName);
        return this;
    }


    public MappedObjectClass build() {
        var connIdAttrs = new HashMap<String, MappedAttribute>();
        var nativeAttrs = new HashMap<String, MappedAttribute>();
        for (var attrBuilder : nativeAttributes.values()) {
            var attribute = attrBuilder.build();
            connIdBuilder.addAttributeInfo(attribute.connId());
            connIdAttrs.put(attribute.connId().getName(), attribute);
            nativeAttrs.put(attribute.remoteName(), attribute);
        }

        return new MappedObjectClass(connIdBuilder.build(), nativeAttrs, connIdAttrs);
    }

    public String description() {
        return description;
    }

    public boolean embedded() {
        // Embedded means non-manageable object
        return false;
    }

    public Iterable<MappedAttributeBuilderImpl> allAttributes() {
        return nativeAttributes.values();
    }

    public boolean connIdAttributeNotDefined(String name) {
        for (var attrBuilder : nativeAttributes.values()) {
            if (name.equals(attrBuilder.connIdBuilder.build().getName())) {
                return false;
            }
        }
        return true;
    }


    private static class ScimBuilder implements ScimMapping {


        private String schemaUri;
        private String name;
        private boolean onlyExplicitlyListed = false;

        @Override
        public ScimMapping extension(String alias, String namespace) {
            return null;
        }

        @Override
        public String schemaUri() {
            return schemaUri;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ScimMapping name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean isOnlyExplicitlyListed() {
            return onlyExplicitlyListed;
        }

        @Override
        public ScimMapping onlyExplicitlyListed(boolean value) {
            onlyExplicitlyListed = value;
            return this;
        }
    }
}
