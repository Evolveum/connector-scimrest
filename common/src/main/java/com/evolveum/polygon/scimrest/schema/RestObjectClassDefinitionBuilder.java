/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestAttributeBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestReferenceAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.HashMap;
import java.util.Map;

public class RestObjectClassDefinitionBuilder extends BaseObjectClassDefinitionBuilder<
        RestObjectClassSchemaBuilder,
        RestObjectClassDefinition,
        RestAttributeBuilder<RestReferenceAttributeBuilder>,
        RestReferenceAttributeBuilder,
        RestAttributeBuilderImpl,
        RestAttributeDefinition> implements RestObjectClassSchemaBuilder {

    private ScimMapping scim;

    public RestObjectClassDefinitionBuilder(RestSchemaBuilderImpl restSchemaBuilder, DefinitionValue<String> name) {
        super(restSchemaBuilder, name);
    }

    @Override
    protected RestAttributeBuilderImpl newAttribute(DefinitionValue<String> def) {
        return new RestAttributeBuilderImpl(this, def);
    }

    @Override
    public RestAttributeBuilderImpl attribute(String name) {
        return (RestAttributeBuilderImpl) super.attribute(name);
    }

    @Override
    public RestAttributeBuilderImpl reference(String name) {
        return (RestAttributeBuilderImpl) super.reference(name);
    }

    /**
     * The SCIM half of the "NAME defaults to a copy of UID" rule (see
     * {@code NameDefaultsToUidRule}): the default {@code __NAME__} attribute copies the UID's
     * SCIM path and wire type, so it reads the same resource property (the resource {@code id})
     * that {@code __UID__} reads. Only plain path+type copies are made — a custom value-mapping
     * implementation on the UID is not inherited; if the UID has no SCIM path or wire type
     * there is nothing to copy.
     */
    @Override
    public RestAttributeBuilderImpl deriveDefaultNameFromUid(RestAttributeBuilderImpl uidAttribute) {
        var uidScim = uidAttribute.scim;
        if (uidScim == null || uidScim.path() == null || uidScim.type() == null) {
            return null;
        }
        if (!findAttributes(a -> Name.NAME.equals(a.name())).isEmpty()) {
            return null;
        }
        var nameAttribute = attribute(Name.NAME);
        nameAttribute.scim();
        nameAttribute.scim.copyFrom(uidScim);
        return nameAttribute;
    }

    @Override
    protected RestObjectClassDefinition buildImpl(ObjectClassInfo connIdInfo, Map<String, RestAttributeDefinition> nativeAttrs, Map<String, RestAttributeDefinition> connIdAttrs) {
        var scimMapping = scim != null ? new RestObjectClassDefinition.ObjectClassScimMapping(scim.name(), scim.schemaUri()) : null;
        return new RestObjectClassDefinition(connIdInfo, nativeAttrs, connIdAttrs, scimMapping);
    }

    @Override
    public ScimMapping scim() {
        if (scim == null) {
            this.scim = new ScimBuilder();
            scim.name(name());
        }
        return this.scim;
    }

    @Override
    public ScimMapping scim(@DelegatesTo(ScimMapping.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    private static class ScimBuilder implements ScimMapping {

        private String schemaUri;
        private String name;
        private boolean onlyExplicitlyListed = false;
        private Map<String, String> aliasToNamespace = new HashMap<>();

        @Override
        public ScimMapping extension(String alias, String namespace) {
            aliasToNamespace.put(alias, namespace);
            return this;
        }

        @Override
        public String schemaUri() {
            return schemaUri;
        }

        @Override
        public ScimMapping schemaUri(String schemaUri) {
            this.schemaUri = schemaUri;
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ScimMapping name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean isOnlyExplicitlyListed() {
            return onlyExplicitlyListed;
        }

        @Override
        public ScimMapping onlyExplicitlyListed(boolean value) {
            onlyExplicitlyListed = value;
            return this;
        }

        @Override
        public String extensionUriFromAlias(String uriOrAlias) {
            if (uriOrAlias.startsWith("urn:")) {
                return uriOrAlias;
            }
            return aliasToNamespace.getOrDefault(uriOrAlias, uriOrAlias);
        }
    }
}
