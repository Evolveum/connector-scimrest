package com.evolveum.polygon.scimrest.impl;

import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.groovy.api.FilterBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestUpdateOperationBuilder;
import com.evolveum.polygon.scimrest.spi.CreateOperation;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.spi.UpdateOperation;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

import static com.evolveum.polygon.scimrest.impl.AttributeAwareOperationHandler.Capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UpdateOperationStrategyHandler implements UpdateOperation {

    private final ConnectorContext context;
    private final ObjectClass objectClass;
    private final Collection<UpdateOperationHandler> handlers;

    public UpdateOperationStrategyHandler(ConnectorContext context, ObjectClass objectClass, Collection<UpdateOperationHandler> handlers) {
        this.context = context;
        this.objectClass = objectClass;
        this.handlers = handlers;
    }

    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        var previousStateRequired = false;
        var outstanding = new HashSet<>(modifications);
        var handlersToUse = new ArrayList<Capability<AttributeDelta,UpdateOperationHandler>>();
        for (UpdateOperationHandler handler : handlers) {
            var support = handler.canHandle(outstanding, options);
            if (support.isUnsupported()) {
                continue;
            }
            // Update information if we need previous state
            if (handler.requiresOriginalState()) {
                previousStateRequired = true;
            }
            outstanding.removeAll(support.supported());
            handlersToUse.add(support);
            if (outstanding.isEmpty()) {
                // NO need to check rest of handlers, if all delta components are processed.
                break;
            }
        }
        if (!outstanding.isEmpty()) {
            // FIXME? Error that we can not update all requested attributes?
            throw new ConnectorException("Attribute deltas are unsupported: " + outstanding);
        }

        ConnectorObject originalState = previousStateRequired ? readObject(uid) : null;

        for (var capability : handlersToUse) {
            var deltasToApply = capability.supported();
            // FIXME: After state should be computed for absolute here? or in handler?
            var request = new RestUpdateOperationBuilder.UpdateRequest(objectClass, uid, capability.supported(), originalState);
            capability.handler().update(request, options);

        }
        return null;
    }

    private ConnectorObject readObject(Uid uid) {
        var result = new ArrayList<ConnectorObject>();
        context.handlerFor(objectClass).checkSupported(ExecuteQueryProcessor.class)
                .executeQuery(context, new EqualsFilter(uid), result::add, null);
        return result.stream().findFirst().orElseThrow(
                () -> new ConnectorException("Can not update object: " + uid + ". Unable to read previous state"));
    }

}
