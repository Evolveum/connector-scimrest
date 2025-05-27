package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.ResultsHandler;

public interface BatchAwareResultHandler extends ResultsHandler {

    void batchFinished();

    static void batchFinished(ResultsHandler handler) {
        if (handler instanceof BatchAwareResultHandler) {
            ((BatchAwareResultHandler) handler).batchFinished();
        }
    }
}
