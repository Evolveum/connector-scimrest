package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ScimResourceContext;
import com.evolveum.polygon.scim.rest.schema.RestAttributeBuilderImpl;
import com.evolveum.polygon.scim.rest.schema.RestObjectClassBuilder;
import com.evolveum.polygon.scim.rest.schema.RestSchemaBuilder;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;

import java.util.HashSet;
import java.util.Set;

public class ScimSchemaTranslator {


    public void populateBuilder(ScimResourceContext scim, RestSchemaBuilder schema) {

        // First we try to match object class by SCIM URN
        var objectClass = findOrCreateObjectClass(scim.resource(), schema);

        if (objectClass.description() == null) {
            objectClass.description(scim.resource().getDescription());
        }
        // We map the object class to this SCIM resource
        objectClass.scim().name(scim.resource().getName());

        populatePrimarySchema(scim.primarySchema(), objectClass, objectClass.scim().isOnlyExplicitlyListed());

    }

    private void populatePrimarySchema(SchemaResource schemaResource, RestObjectClassBuilder objectClass, boolean onlyListed) {
        for (var scimAttr : schemaResource.getAttributes()) {
            // We should determine if attribute is excluded by name or being structured
            if (AttributeDefinition.Type.COMPLEX.equals(scimAttr.getType())) {
                // skip for now
                continue;
            }

            // Lookup as SCIM attribute first
            var attribute = findOrCreateAttribute(scimAttr, objectClass, onlyListed);



        }
    }

    private RestAttributeBuilderImpl findOrCreateAttribute(AttributeDefinition scimAttr, RestObjectClassBuilder objectClass, boolean onlyListed) {
        for (var attr : objectClass.allAttributes()) {
            if (scimAttr.getName().equals(attr.scim().name())) {
                return attr;
            }
        }
        if (!onlyListed) {
            return null;
        }
        // We create new attribute (or use attribute with same name
        return objectClass.attribute(scimAttr.getName());

    }

    private RestObjectClassBuilder findOrCreateObjectClass(ResourceTypeResource scim, RestSchemaBuilder schema) {
        for (var objClass : schema.allObjectClasses()) {
            if (objClass.embedded()) {

            }
            // We Try to match existing builder by name of SCIM resources
            // this allows for renames based on schema
            if (scim.getName().equals(objClass.scim().name()) ) {
                return objClass;
            }
        }

        for (var objClass : schema.allObjectClasses()) {
            // We Try to match existing builder by uri of scim schema
            // this allows for renames based on schema
            if (scim.getSchema().toString().equals(objClass.scim().schemaUri()) ) {
                return objClass;
            }
        }

        //
        return schema.objectClass(scim.getName());

    }


}
