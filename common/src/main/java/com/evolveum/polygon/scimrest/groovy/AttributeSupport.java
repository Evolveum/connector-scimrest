package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AttributePath;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
