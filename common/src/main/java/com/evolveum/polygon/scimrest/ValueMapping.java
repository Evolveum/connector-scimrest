/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest;

import java.util.Set;

/**
 * Represents a mapping between data types used in an ConnId
 * and their corresponding wire types used in the protocol representation.
 * This interface allows for conversions between these two types and provides
 * metadata about the supported data types.
 */
public interface ValueMapping<C,P> {

    Class<? extends C> connIdType();
    Class<? extends P> primaryWireType();
    default Set<Class<? extends P>> supportedWireTypes() {
        return Set.of(primaryWireType());
    }

    P toWireValue(C value) throws  IllegalArgumentException;

    C toConnIdValue(P value) throws IllegalArgumentException;

}
