package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.Deferred;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolutionContext;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolver;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scim.rest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scim.rest.schema.MappedAttribute;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ScriptedSingleAttributeResolverBuilder implements AttributeResolverBuilder {

    private final String objectClass;
    private final Deferred<MappedAttribute> attribute;
    private ResolutionType resolutionType = ResolutionType.PER_OBJECT;
    private Implementation implementation;

    public ScriptedSingleAttributeResolverBuilder(String objectClass, Deferred<MappedAttribute> attribute) {
        this.objectClass = objectClass;
        this.attribute = attribute;
    }

    @Override
    public ScriptedSingleAttributeResolverBuilder attribute(String attributeName) {
       // Declaring multiple attributes is not suppported.
        return this;
    }

    @Override
    public ScriptedSingleAttributeResolverBuilder resolutionType(ResolutionType type) {
        this.resolutionType = type;
        return this;
    }

    @Override
    public AttributeResolverBuilder search(@DelegatesTo(AttributeResolutionContext.class) Closure<Filter> closure) {
        this.implementation = new SearchBased(closure);
        return this;
    }

    @Override
    public ScriptedSingleAttributeResolverBuilder implementation(@DelegatesTo(AttributeResolutionContext.class) Closure<?> closure) {
        this.implementation = new ClosureBased(closure);
        return this;
    }

    abstract class Implementation {

        abstract AttributeResolver build();
    }

    private class SearchBased extends Implementation {
        private final Closure<Filter> closure;

        public SearchBased(Closure<Filter> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
           var attrDef = attribute.get();
            if (ConnectorObjectReference.class.equals(attrDef.connId().getType())) {
                var targetObjectClass = attrDef.connId().getReferencedObjectClassName();
                return new ScriptedAttributeResolverBuilder.GroovySearchBasedReference(attrDef, targetObjectClass,closure);
            }
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private class ClosureBased extends Implementation {
        private final Closure<?> closure;

        public ClosureBased(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
            return new ScriptedAttributeResolverBuilder.GroovySingleResolver(objectClass, Set.of(attribute.get()), closure);
        }
    }

    public AttributeResolver build() {
        if (implementation == null) {
            return null;
        }

        return implementation.build();
    }

    public ResolutionType resolutionType() {
        return resolutionType;
    }


}
