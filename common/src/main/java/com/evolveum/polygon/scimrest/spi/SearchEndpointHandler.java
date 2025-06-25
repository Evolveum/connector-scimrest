/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.spi;

import com.evolveum.polygon.scimrest.groovy.RestSearchOperationHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface SearchEndpointHandler<BF, OF> {


    RestSearchOperationHandler process(Filter filter);
}
