package com.evolveum.polygon.scim.rest.schema;

import com.unboundid.scim2.common.GenericScimResource;

public interface AttributeProtocolMapping<PO> {

    Object extractValue(PO remoteObj);

}
