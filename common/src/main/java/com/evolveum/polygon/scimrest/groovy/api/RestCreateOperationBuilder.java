package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;

import java.util.Set;

public interface RestCreateOperationBuilder extends RestOperationBuilder<RestCreateOperationBuilder.Endpoint> {

    Endpoint endpoint(HttpMethod method, String path);

    @Override
    default Endpoint endpoint(String path) {
        return endpoint(HttpMethod.POST, path);
    }

    default Endpoint endpoint(String path, @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(path));
    }

    default Endpoint endpoint(HttpMethod method, String path, @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(method, path));
    }

    interface Endpoint extends EndpointBuilder.SingleObject<Set<Attribute>, ConnectorObject> {

        Endpoint supportedAttributes(String... attributes);
    }
}
