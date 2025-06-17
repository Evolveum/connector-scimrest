package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ConnIdMapping {

    public static AttributeMapping<?, ?> of(String name,  AttributeMapping<?,?> backingMapping) {
        if (Uid.NAME.equals(name)) {
            return new AttributeMapping<String, Object>() {
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
                    return ((AttributeMapping) backingMapping).toWireValue(value);
                }

                @Override
                public String toConnIdValue(Object value) throws IllegalArgumentException {
                    return Objects.toString(value);
                }

                @Override
                public List toConnIdValues(Iterable wireValues) {
                    var originalList = ((AttributeMapping) backingMapping).toConnIdValues(wireValues);
                    if (originalList != null && !originalList.isEmpty()) {
                        return List.of(Objects.toString(originalList.get(0)));
                    }
                    return List.of();
                }
            };
        }
        return backingMapping;
    }
}
