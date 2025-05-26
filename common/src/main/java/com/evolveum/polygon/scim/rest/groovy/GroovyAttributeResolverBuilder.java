package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.schema.RestAttribute;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.HashSet;
import java.util.Set;

public class GroovyAttributeResolverBuilder<HC> implements AttributeResolverBuilder<HC> {

    private final Set<RestAttribute> attributes = new HashSet<>();

    private final ConnectorContext context;
    private final RestObjectClass objectClass;
    private ResolutionType resolutionType;

    public GroovyAttributeResolverBuilder(ConnectorContext context, RestObjectClass objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public GroovyAttributeResolverBuilder<HC> attribute(String attributeName) {
        attributes.add(objectClass.attributeFromProtocolName(attributeName));
        return this;
    }

    @Override
    public GroovyAttributeResolverBuilder<HC> resolutionType(ResolutionType type) {
        this.resolutionType = type;
        return this;
    }

    @Override
    public GroovyAttributeResolverBuilder<HC> implementation(@DelegatesTo(AttributeResolutionContext.class) Closure<?> closure) {
        // FIXME: Capture implementation
        return this;
    }


}
