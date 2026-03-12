package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.RestCreateOperationBuilderImpl;
import com.evolveum.polygon.scimrest.groovy.Script;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimUpdateBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimUpdateHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.Set;

public interface RestUpdateOperationBuilder extends RestObjectOperationBuilder<RestUpdateOperationBuilder.Endpoint> {

    ScimUpdateBuilder scim();

    default ScimUpdateBuilder scim(@DelegatesTo(value = ScimUpdateBuilder.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, scim());
    }


    @Override
    Endpoint endpoint(HttpMethod method, String path);

    @Override
    default Endpoint endpoint(String path) {
        return endpoint(HttpMethod.PUT, path);
    }

    @Override
    default Endpoint endpoint(String path,
                              @DelegatesTo(value = EndpointBuilder.SingleObject.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        return endpoint(PUT, path, value);
    }

    @Override
    default Endpoint endpoint(HttpMethod method, String path,
                              @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        var endpoint = endpoint(method, path);
        return GroovyClosures.callAndReturnDelegate(value, endpoint);
    }

    interface AttributeSpecific<A extends AttributeValueFilter, T extends AttributeSpecific<A,T>> {

        A supportedAttribute(String attributeName);

        A supportedAttribute(String attributeName,
                             @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                             @Script.Initialization
                             Closure<?> closure);

        default T supportedAttributes(String... attributes) {
            for (var attribute : attributes) {
                supportedAttribute(attribute);
            }

            //noinspection unchecked
            return (T) this;
        }

    }

    interface Endpoint extends AttributeSpecific<AttributeValueFilter, Endpoint>, EndpointBuilder.SingleObject<UpdateRequest, Set<AttributeDelta>> {


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
