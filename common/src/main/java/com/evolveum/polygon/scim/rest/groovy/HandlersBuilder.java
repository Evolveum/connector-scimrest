package com.evolveum.polygon.scim.rest.groovy;

import java.util.Map;

public class HandlersBuilder {

    Map<String, ObjectClassHandlerBuilder> handlers;

    public ObjectClassHandlerBuilder objectClass(String user) {
        var handler = handlers.computeIfAbsent(user, k -> new ObjectClassHandlerBuilder());
        return handler;
    }
}
