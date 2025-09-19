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
    embedded(true);
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

//    attribute("principal") {
//
//        json {
//            path attribute("_links").child("principal")
//        }
//
//        complexType "Principal"
//        readable true;
//        updateable false;
//        creatable true;
//        returnedByDefault true;
//        required true;
//    }

    reference("principal") {
        objectClass "Principal"

        json {
            path attribute("_links").child("principal")
            implementation {
                deserialize {

                    var href = value.get("href")?.asText();
                    var pid = href.substring(href.lastIndexOf("/") + 1)

                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("Principal"))
                            .setUid(pid)
                            .setName(value.get("title")?.asText())
                    return new ConnectorObjectReference(obj.build());
                }
            }
        }
    }

    reference("roles") {
        objectClass "Role"

        json {
            path attribute("_links").child("roles")
            implementation {
                deserialize {
                    if(it == null){
                        it = value;
                    }
                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("Role"))
                            .setUid(it.get("href")?.asText())
                            .setName(it.get("title")?.asText())
                    return new ConnectorObjectReference(obj.build());
                }
            }
        }
    }

    reference("project") {
        objectClass "Project"
//        relationship("MembershipProject"){
//            subject("Membership"){}
//            object("Project"){}
//        }
        json {
            path attribute("_links").child("project")
            implementation {
                deserialize {

                    if(it == null){
                        it = value;
                    }
                    var href = it.get("href")?.asText();
                    var pid = href.substring(href.lastIndexOf("/") + 1)

                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("Project"))
                            .setUid(pid)
                            .setName(it.get("title")?.asText())
                    return new ConnectorObjectReference(obj.build());
                }
            }
        }
    }
}
