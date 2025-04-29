package com.evolveum.polygon.scim.rest;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.*;

import java.util.List;
import java.util.Set;

/**
 * Generic Base Connector with support for separate handlers per Object Class
 *
 * This allows for example to mix-and-match SCIM handlers for users, with Scripted Handlers
 * for groups or repositories (eg. Github, which allows only user provisioning via SCIM).
 * or pure REST connector with customized handlers per object class (eg. Forgejo).
 *
 * The retrieval of correct Object Class handler should be done in {@link #handlerFor(ObjectClass)} method.
 *
 * Handlers needs to implement {@link ObjectClassHandler} interface.
 *
 */
public abstract class ClassHandlerConnectorBase implements Connector,
        AuthenticateOp, CreateOp, DeleteOp, ResolveUsernameOp,
        SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<Filter>, TestOp,
        UpdateDeltaOp {

    public abstract ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException;

    @Override
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password, OperationOptions options) {
        return handlerFor(objectClass).checkSupported(AuthenticateOp.class).authenticate(username, password, options);
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        return handlerFor(objectClass).checkSupported(CreateOp.class).create(createAttributes, options);
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        handlerFor(objectClass).checkSupported(DeleteOp.class).delete(uid, options);
    }

    @Override
    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        return handlerFor(objectClass).checkSupported(ResolveUsernameOp.class).resolveUsername(username, options);
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return List::of;
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        handlerFor(objectClass).checkSupported(SearchOp.class).executeQuery(query, handler, options);
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objclass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        return handlerFor(objclass).checkSupported(UpdateDeltaOp.class).updateDelta(uid, modifications, options);
    }
}
