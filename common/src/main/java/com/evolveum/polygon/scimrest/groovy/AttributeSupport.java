package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;

import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;

import java.util.*;

public record AttributeSupport(MappedAttribute attributeInfo, Collection<Object> values, Collection<Transition> transitions) {


    record Transition(Object from, Object to) {

    }

    public boolean isSupported(Attribute attribute) {
        if (attribute == null) {
            return false;
        }
        if (!attributeInfo.connId().getName().equals(attribute.getName())) {
            return false;
        }
        if (values == null && transitions == null) {
            // Supports all values (no need to enumerate them)
            return true;
        }
        if (values == null) {
            // Supports only transitions
            return false;
        }
        return values.containsAll(attribute.getValue());
    }

    public boolean isSupported(AttributeDelta delta) {
        if (delta == null) {
            return false;
        }
        if (!attributeInfo.connId().getName().equals(delta.getName())) {
            return false;
        }
        if (values == null && transitions == null) {
            return true;
        }
        if (values != null && values.containsAll(delta.getValuesToAdd()) && values.containsAll(delta.getValuesToReplace())) {
            return true;
        }
        if (transitions != null) {
            // FIXME: implement transition checking
            for (Transition transition : transitions) {

            }

        }
        return false;
    }


    public static class SupportBuilder<T extends RestUpdateOperationBuilder.AttributeSpecific<RestUpdateOperationBuilder.AttributeValueFilter, T>> implements RestUpdateOperationBuilder.AttributeSpecific<RestUpdateOperationBuilder.AttributeValueFilter,T> {

        private final T parent;
        protected final Map<String, Builder> supportedAttributes = new HashMap<>();

        public SupportBuilder(T parent) {
            this.parent = parent;
        }

        public T supportedAttributes(String... attributes) {
            for (String attribute : attributes) {
                var attrBuilder = supportedAttribute(attribute);
                // Mark supported here
            }
            return parent;
        }

        public AttributeSupport.Builder supportedAttribute(String attribute) {
            return supportedAttributes.computeIfAbsent(attribute, k -> new AttributeSupport.Builder());
        }

        @Override
        public AttributeSupport.Builder supportedAttribute(String attributeName, Closure<?> closure) {
            var attr = supportedAttribute(attributeName);
            return GroovyClosures.callAndReturnDelegate(closure, attr);
        }

        public Set<Map.Entry<String, Builder>> entries() {
            return supportedAttributes.entrySet();
        }
    }

    public static class Builder implements RestUpdateOperationBuilder.AttributeValueFilter {

        private Collection<Object> values;
        private Collection<Transition> transitions;


        @Override
        public RestUpdateOperationBuilder.AttributeValueFilter value(Object value) {
            if (values == null) {
                values = new ArrayList<>();
            }
            values.add(value);
            return this;
        }

        @Override
        public RestUpdateOperationBuilder.AttributeValueFilter transition(Object oldValue, Object newValue) {
            if (transitions == null) {
                transitions = new ArrayList<>();
            }
            transitions.add(new Transition(oldValue, newValue));
            return this;
        }

        public AttributeSupport build(MappedAttribute attribute) {
            return new AttributeSupport(attribute, values == null ? null : Set.copyOf(values),
                    transitions == null ? null : Set.copyOf(transitions));
        }
    }
}
