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
 * <p>
 * The {@code id} attribute is materialized (find-or-create) and bound to {@code __UID__}:
 * <ul>
 *   <li>when the SCIM schema lists {@code id} — the declared wire type (set by
 *       {@code ScimTypeToJsonTypeRule}) is kept as-is, only the missing pieces are filled in;</li>
 *   <li>when a Groovy definition already provides it (explicit {@code scim { name "id" }} or a
 *       native attribute named {@code id}) — even if the SCIM schema doesn't list it;</li>
 *   <li>otherwise, for object classes that aren't {@code onlyExplicitlyListed} — a plain
 *       {@code id} attribute is created, since every SCIM resource has an {@code id}.</li>
 * </ul>
 * The wire type is only defaulted to {@code string} (per the SCIM standard) when nothing
 * declared one; a schema- or Groovy-declared type is never overridden.
 */
public class ScimUidDetectionRule implements ScimResourceMappingRule {

    private static final String ID_ATTR_NAME = "id";
    private static final String DEFAULT_ID_TYPE = "string";

    @Override
    public boolean checkIfApplicable(ScimResourceContext resource, RestObjectClassSchemaBuilder objectClass, RestAttributeBuilder<RestReferenceAttributeBuilder> attribute) {
        if (resource.primarySchema() == null) {
            return false;
        }
        var idDefined = findIdAttribute(objectClass) != null;
        return idDefined || !objectClass.scim().isOnlyExplicitlyListed();
    }

    @Override
    public ScimMappingAction createAction(ScimResourceContext resource) {
        return new ScimMappingAction() {
            @Override
            public void applyToSchema(RestObjectClassSchemaBuilder objectClass) {
                var idAttribute = findIdAttribute(objectClass);
                if (idAttribute == null) {
                    if (objectClass.scim().isOnlyExplicitlyListed()) {
                        return;
                    }
                    idAttribute = objectClass.attribute(ID_ATTR_NAME);
                }
                idAttribute.scim().name(ID_ATTR_NAME);
                if (idAttribute.scim().type() == null) {
                    idAttribute.scim().type(DEFAULT_ID_TYPE);
                }
                boolean uidAlreadyDefined = !objectClass.findAttributes(
                        a -> Uid.NAME.equals(a.connId().name().value())).isEmpty();
                if (!uidAlreadyDefined) {
                    idAttribute.connId().name(Uid.NAME);
                }
            }
        };
    }

    private static RestAttributeBuilder<RestReferenceAttributeBuilder> findIdAttribute(RestObjectClassSchemaBuilder objectClass) {
        var maybe =  objectClass.findAttributes(a -> ID_ATTR_NAME.equals(a.scim().name()));
        if (maybe.isEmpty()) {
            maybe = objectClass.findAttributes(a -> ID_ATTR_NAME.equals(a.name()));
        }
        return maybe.stream().findFirst().orElse(null);
    }
}
