package com.evolveum.polygon.scim.rest.schema;

import java.util.List;

public interface AttributeProtocolMapping<PO> {

    Object extractValue(PO remoteObj);

    Class<?> connIdType();

    List<Object> extractMultipleValues(PO wireValues);
}
