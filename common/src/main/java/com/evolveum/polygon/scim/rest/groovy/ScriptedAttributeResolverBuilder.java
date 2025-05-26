package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolutionContext;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scim.rest.schema.RestAttribute;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashSet;
import java.util.Set;

public class ScriptedAttributeResolverBuilder<HC> implements AttributeResolverBuilder {

    private final Set<RestAttribute> attributes = new HashSet<>();

    private final ConnectorContext context;
    private final RestObjectClass objectClass;
    private ResolutionType resolutionType;

    public ScriptedAttributeResolverBuilder(ConnectorContext context, RestObjectClass objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public ScriptedAttributeResolverBuilder<HC> attribute(String attributeName) {
        attributes.add(objectClass.attributeFromProtocolName(attributeName));
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder<HC> resolutionType(ResolutionType type) {
        this.resolutionType = type;
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder<HC> implementation(@DelegatesTo(AttributeResolutionContext.class) Closure<?> closure) {
        // FIXME: Capture implementation
        return this;
    }


}
