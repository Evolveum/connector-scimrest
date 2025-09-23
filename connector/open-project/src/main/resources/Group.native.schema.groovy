import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectReference
import org.identityconnectors.framework.common.objects.ObjectClass

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Group") {
    description "Team represents a team in an organization"

    attribute("id") {
        jsonType "integer";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required true;
        description "User’s id";
    }
    attribute("name") {
        jsonType "string"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
        description "Group’s full name, formatting depends on instance settings";
    }
    attribute("created_at") {
        jsonType "string"
        openApiFormat "date-time";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required false;
        description "Time of creation";
    }
    attribute("updated_at") {
        jsonType "string"
        openApiFormat "date-time";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required false;
        description "Time of the most recent change to the user";
    }
    reference("members") {
        description("The list all all the users that are members of the group")
        objectClass "User"

        json {
            path attribute("_links").child("members")
            implementation {
                deserialize {

                    var href = value.get("href")?.asText();
                    var pid = href.substring(href.lastIndexOf("/") + 1)

                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("User"))
                            .setUid(pid)
                            .setName(value.get("title")?.asText())
                    return new ConnectorObjectReference(obj.build());
                }
            }
        }
    }
}