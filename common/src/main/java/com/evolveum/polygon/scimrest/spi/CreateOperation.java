package com.evolveum.polygon.scimrest.spi;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.Set;

public interface CreateOperation extends ObjectClassOperation {

    ConnectorObject create(Set<Attribute> createAttributes, OperationOptions options);

}
