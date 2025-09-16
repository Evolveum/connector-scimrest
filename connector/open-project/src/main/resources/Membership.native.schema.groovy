/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectReference
import org.identityconnectors.framework.common.objects.ObjectClass

objectClass("Membership") {
    attribute("id") {
        jsonType "integer";
        readable true;
        returnedByDefault true;
        required true;
        description "Membership id";
    }
    attribute("name") {
        json {
            type "integer"
            name "id"
        };
        readable true;
        returnedByDefault true;
        required true;
        description "Membership name (copy of id)";
    }

    attribute("createdAt") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        returnedByDefault true;
        description "Time of creation";
    }

    attribute("updatedAt") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        returnedByDefault true;
        description "Time of latest update";
    }
    reference("roles") {
        objectClass "Role"
        json {
            path attribute("_links").child("roles")
            implementation {
                deserialize {
                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("Role"))
                            .setUid(it.get("href").asText())
                            .setName(it.get("title").asText())
                    return new ConnectorObjectReference(obj.build());
                }
            }
        }
    }
}
