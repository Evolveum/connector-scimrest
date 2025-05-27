package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolutionContext;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolver;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scim.rest.schema.RestAttribute;
import com.evolveum.polygon.scim.rest.schema.RestObjectClass;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

import java.util.HashSet;
import java.util.Set;

public class ScriptedAttributeResolverBuilder<HC> implements AttributeResolverBuilder {

    private final Set<RestAttribute> attributes = new HashSet<>();

    private final ConnectorContext context;
    private final RestObjectClass objectClass;
    private ResolutionType resolutionType = ResolutionType.PER_OBJECT;
    private Closure<?> implementation;

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
        this.implementation = closure;
        return this;
    }


    public AttributeResolver build() {
        return new GroovySingleResolver(context, objectClass, Set.copyOf(attributes), implementation);
    }

    public ResolutionType resolutionType() {
        return resolutionType;
    }

    private record GroovySingleResolver(ConnectorContext context,
                                        RestObjectClass objectClass,
                                        Set<RestAttribute> supportedAttributes,
                                        Closure<?> implementation) implements AttributeResolver {


        @Override
        public Set<RestAttribute> getSupportedAttributes() {
            return supportedAttributes;
        }

        @Override
        public void resolveSingle(ConnectorObjectBuilder builder) {
            var scriptContext = new SingleResolverContext(context, objectClass, builder);
            GroovyClosures.copyAndCall(implementation, scriptContext);
        }
    }

    private record SingleResolverContext(ConnectorContext context, RestObjectClass definition, ConnectorObjectBuilder value) implements AttributeResolutionContext {

        @Override
        public ObjectClassScripting objectClass(String name) {
            return ObjectClassScriptingFacade.from(context, name);
        }
    }
}
