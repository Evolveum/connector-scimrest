package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Objects;
import java.util.Set;

public class ConnIdMapping {

    public static AttributeMapping of(AttributeInfo info, AttributeMapping backingMapping) {
        if (Uid.NAME.equals(info.getName())) {
            return new AttributeMapping() {
                @Override
                public Class<?> connIdType() {
                    return String.class;
                }

                @Override
                public Class<?> primaryWireType() {
                    return backingMapping.primaryWireType();
                }

                @Override
                public Set<Class<?>> supportedWireTypes() {
                    return backingMapping.supportedWireTypes();
                }

                @Override
                public Object toWireValue(Object value) throws IllegalArgumentException {
                    return backingMapping.toWireValue(value);
                }

                @Override
                public Object toConnIdValue(Object value) throws IllegalArgumentException {
                    return Objects.toString(value);
                }
            };
        }
        return backingMapping;
    }
}
