package com.evolveum.polygon.scimrest.impl;

import com.evolveum.polygon.scimrest.groovy.ConnectorContext;
import com.evolveum.polygon.scimrest.spi.CreateOperation;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.spi.UpdateOperation;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CreateOperationStrategyHandler implements CreateOperation {

    private final ConnectorContext context;
    private final ObjectClass objectClass;
    private final Collection<CreateOperationHandler> handlers;

    public CreateOperationStrategyHandler(ConnectorContext context, ObjectClass objectClass, Collection<CreateOperationHandler> handlers) {
        this.context = context;
        this.objectClass = objectClass;
        this.handlers = handlers;
    }

    @Override
    public ConnectorObject create(Set<Attribute> createAttributes, OperationOptions options) {
        
        var handler = selectMostSpecific(createAttributes, options);
        // Create object
        var result = handler.create(createAttributes, options);
        if (result == null) {
            throw new ConnectorException("Problem creating object.");
        }
        if (updatesNeeded(result, createAttributes)) {
            // walk thru update processors and create delta? or just create updates?
            var delta = computeNecessaryUpdateDelta(result, createAttributes);
            context.handlerFor(objectClass).checkSupported(UpdateOperation.class);
        
        
        }
        return result.object();
    }

    private Set<BaseAttributeDelta> computeNecessaryUpdateDelta(CreateOperationHandler.Result result, Set<Attribute> requestedAttributes) {
        var createdObject = result.object();
        if (createdObject == null) {
            createdObject = readObject(result.uid());
        }
        // FIXME: Here we should compute delta
        var ret = new HashSet<BaseAttributeDelta>();
        for (var requested : requestedAttributes) {
            var created = createdObject.getAttributeByName(requested.getName());
            if (created == null) {
                // Compare values
                ret.add(deltaFrom(requested));
            }
        }
        return ret;
    }

    private ConnectorObject readObject(Uid uid) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private BaseAttributeDelta deltaFrom(Attribute requested) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private boolean updatesNeeded(CreateOperationHandler.Result result, Set<Attribute> createAttributes) {
        // FIXME: Determine based on attribute set and existing object if additional updates are neccessary
        return false;
    }

    private CreateOperationHandler selectMostSpecific(Set<Attribute> request, OperationOptions options) {
        // FIXME: Consider capabilities & handler which can handle most of the attributes
        for (var handler : handlers) {
            var support = handler.canHandle(request, options);
            if (support.isUnsupported()) {
                continue;
            }
            return handler;
        }
        return null;
    }
}
