package com.evolveum.polygon.scimrest.groovy.api.scim;


import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.scimrest.groovy.Script;
import com.evolveum.polygon.scimrest.groovy.api.SearchHandlerBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ScimSearchBuilder extends ScimOperationBuilder<ScimSearchBuilder.Limitations, ScimSearchBuilder> {

    @Override
    default Limitations limitations(@DelegatesTo(value = Limitations.class, strategy = Closure.DELEGATE_ONLY)
                            @Script.Initialization
                            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, limitations());
    }

    interface Limitations extends ScimOperationBuilder.Limitations, SearchHandlerBuilder<Limitations> {

    }

}
