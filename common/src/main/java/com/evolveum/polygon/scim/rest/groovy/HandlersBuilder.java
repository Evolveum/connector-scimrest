package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.AbstractRestJsonObjectClassHandler;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HandlersBuilder {

    Map<String, ObjectClassHandlerBuilder> handlers = new HashMap<>();

    public ObjectClassHandlerBuilder objectClass(String user) {
        var handler = handlers.computeIfAbsent(user, k -> new ObjectClassHandlerBuilder());
        return handler;
    }

    public Map<ObjectClass, AbstractRestJsonObjectClassHandler> build() {
        Map<ObjectClass, AbstractRestJsonObjectClassHandler> ret = new HashMap<>();
        for (var builder : handlers.values()) {
            var handler = builder.build();
            if (handler != null) {
                ret.put(handler.objectClass(), handler);
            }
        }
        return ret;
    }
}
