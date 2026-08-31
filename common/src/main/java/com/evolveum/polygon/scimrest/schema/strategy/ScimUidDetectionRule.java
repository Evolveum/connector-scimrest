/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema.strategy;

import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import com.evolveum.polygon.scimrest.impl.scim.ScimResourceContext;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.evolveum.polygon.scimrest.schema.ScimResourceMappingRule;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Detects the {@code id} attribute on every SCIM resource and maps it to
 * the Connector Framework {@link Uid} attribute.
 */
public class ScimUidDetectionRule implements ScimResourceMappingRule {

    private static final String ID_ATTR_NAME = "id";

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, RestObjectClassSchemaBuilder objectClass, RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        return resource.primarySchema() != null
                && resource.primarySchema().getAttributes().stream()
                        .anyMatch(a -> ID_ATTR_NAME.equals(a.getName()));
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource) {
        return new ScimMappingAction() {
            @Override
            public void applyToSchema(RestObjectClassSchemaBuilder objectClass) {
                for (var attr : objectClass.findAttributes(a -> ID_ATTR_NAME.equals(a.scim().name()))) {
                    attr.scim().name(ID_ATTR_NAME).type("string");
                    boolean uidAlreadyDefined = !objectClass.findAttributes(
                            a -> Uid.NAME.equals(a.connId().name().value())).isEmpty();
                    if (!uidAlreadyDefined) {
                        attr.connId().name(Uid.NAME);
                    }
                    break;
                }
            }
        };
    }
}
