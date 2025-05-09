package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.schema.RestSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public class RestHandlerBuilder {

    private final RestSchema schema;
    private final Map<String, ObjectClassHandlerBuilder<RestContext>> handlers = new HashMap<>();

    public RestHandlerBuilder(RestSchema schema) {
        this.schema = schema;
    }

    public ObjectClassHandlerBuilder<RestContext> objectClass(String user) {
        return handlers.computeIfAbsent(user, k ->  new ObjectClassHandlerBuilder<>(schema.objectClass(user)));
    }

    public Map<ObjectClass, ObjectClassHandler<RestContext>> build() {
        Map<ObjectClass, ObjectClassHandler<RestContext>> ret = new HashMap<>();
        for (var builder : handlers.values()) {
            var handler = builder.build();
            if (handler != null) {
                ret.put(handler.objectClass(), handler);
            }
        }
        return ret;
    }
}
