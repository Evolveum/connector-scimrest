package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.RestCreateOperationBuilderImpl;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.Set;

public interface RestUpdateOperationBuilder extends RestObjectOperationBuilder<RestUpdateOperationBuilder.Endpoint> {



    @Override
    Endpoint endpoint(HttpMethod method, String path);

    @Override
    default Endpoint endpoint(String path) {
        return endpoint(HttpMethod.PUT, path);
    }

    @Override
    default Endpoint endpoint(String path,
                                          @DelegatesTo(value = EndpointBuilder.SingleObject.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        return endpoint(PUT, path, value);
    }

    @Override
    default Endpoint endpoint(HttpMethod method, String path, @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY) Closure<?> value) {
        var endpoint = endpoint(method, path);
        return GroovyClosures.callAndReturnDelegate(value, endpoint);
    }

    interface Endpoint extends EndpointBuilder.SingleObject<UpdateRequest, Set<AttributeDelta>> {

        AttributeValueFilter supportedAttribute(String attributeName, @DelegatesTo(AttributeValueFilter.class) Closure<?> closure);

    }

    interface AttributeValueFilter {

        /**
         * Value, which should be accepted and processed
         **/
        AttributeValueFilter value(Object value);

        AttributeValueFilter transition(Object oldValue, Object newValue);
    }

    record UpdateRequest(ObjectClass clazz, Uid uid, Collection<AttributeDelta> attributeDeltaSet, ConnectorObject before) {
    }

    record UpdateResponse(Uid uid, Set<AttributeDelta> changesApplied) {

    }
}
