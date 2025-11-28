package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.spi.ObjectClassOperation;

public interface RestObjectOperationBuilder<T extends ObjectClassOperation> {


    T build();

}
