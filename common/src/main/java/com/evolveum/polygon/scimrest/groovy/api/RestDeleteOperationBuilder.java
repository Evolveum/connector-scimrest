package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Uid;

public interface RestDeleteOperationBuilder extends RestObjectOperationBuilder<RestDeleteOperationBuilder.Endpoint> {

    @Override
    Endpoint endpoint(HttpMethod method, String path);


    @Override
    default Endpoint endpoint(String path) {
        return endpoint(HttpMethod.DELETE, path);
    }

    @Override
    default Endpoint endpoint(HttpMethod method, String path,
                                      @DelegatesTo(value = EndpointBuilder.SingleObject.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(method, path));
    }

    @Override
    default Endpoint endpoint(String path, @DelegatesTo(value = EndpointBuilder.SingleObject.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(path));
    }

    interface Endpoint extends EndpointBuilder.SingleObject<Uid,Void> {

    }
}
