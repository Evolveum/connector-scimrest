package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.Script;
import com.evolveum.polygon.scimrest.groovy.api.scim.ScimCreateBuilder;
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

    default Endpoint endpoint(HttpMethod method, String path,
                              @DelegatesTo(value = Endpoint.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> value) {
        return GroovyClosures.callAndReturnDelegate(value, endpoint(method, path));
    }

    ScimCreateBuilder scim();

    default ScimCreateBuilder scim(@DelegatesTo(value = ScimCreateBuilder.class, strategy = Closure.DELEGATE_ONLY)
                                   @Script.Initialization
                                   Closure<?> value ) {
        return GroovyClosures.callAndReturnDelegate(value, scim());
    }

    interface Endpoint extends EndpointBuilder.SingleObject<Set<Attribute>, ConnectorObject>, RestUpdateOperationBuilder.AttributeSpecific<RestUpdateOperationBuilder.AttributeValueFilter, Endpoint> {


        @Override
        default RestUpdateOperationBuilder.AttributeValueFilter supportedAttribute(String attributeName,
                                                            @DelegatesTo(value = RestUpdateOperationBuilder.AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                                                            @Script.Initialization
                                                            Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, supportedAttribute(attributeName));
        }
    }

}
