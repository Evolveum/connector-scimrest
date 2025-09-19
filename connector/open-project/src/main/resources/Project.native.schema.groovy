import org.identityconnectors.framework.common.objects.AttributeInfo

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Project") {
    description "Projects are containers structuring the information (e.g. work packages, wikis) into smaller groups."

    attribute("id") {
        jsonType "integer";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
        description "Projects’ id";
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
        description "Time of the most recent change to the project";
    }

    attribute("identifier") {
        jsonType "string"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
    }

    attribute("active") {
        jsonType "boolean"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required false;
        description "Indicates whether the project is currently active or already archived";
    }


    attribute("statusExplanation") {
        complexType "Formattable"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required false;
    }

    attribute("public") {
        jsonType "boolean"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required false;
        description "Indicates whether the project is accessible for everybody";
    }


    attribute("description") {

        complexType "Formattable"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required false;
    }

}