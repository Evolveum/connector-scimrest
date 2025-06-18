package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Deprecated
public class ConnIdMapping {

    public static ValueMapping<?, ?> of(String name, ValueMapping<?,?> backingMapping) {
        if (Uid.NAME.equals(name)) {
            return new ValueMapping<String, Object>() {
                @Override
                public Class<String> connIdType() {
                    return String.class;
                }

                @Override
                public Class<?> primaryWireType() {
                    return backingMapping.primaryWireType();
                }

                @Override
                public Set<Class<?>> supportedWireTypes() {
                    return (Set) backingMapping.supportedWireTypes();
                }

                @Override
                public Object toWireValue(String value) throws IllegalArgumentException {
                    return ((ValueMapping) backingMapping).toWireValue(value);
                }

                @Override
                public String toConnIdValue(Object value) throws IllegalArgumentException {
                    return Objects.toString(value);
                }
            };
        }
        return backingMapping;
    }
}
