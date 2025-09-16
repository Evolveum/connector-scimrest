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

    //TODO complex type, relevant?, embedded=true
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

    //TODO complex type,embedded=true
//    attribute("description") {
//    readable true;
//    updateable true;
//    creatable true;
//    returnedByDefault true;
//    required false;
//    description "Array of all ancestors of the project, down from the root node (first element) to the parent (last element).";
//    }

    //TODO complex type, relevant=false?, reference=true
//    attribute("ancestors") {
//    readable true;
//    updateable false;
//    creatable false;
//    returnedByDefault false;
//    required false;
    //multiValued true
//    description "Array of all ancestors of the project, down from the root node (first element) to the parent (last element).";
//    }

    //TODO complex type, relevant=false? ,reference=true
//    attribute("categories") {
//        readable true;
//        updateable false;
//        creatable false;
//        returnedByDefault false;
//        required false;
    //    multiValued true
//        description "Categories available in this project";
//    }

    //TODO complex type, relevant=false?, reference=true
//    attribute("types") {
//        readable true;
//        updateable false;
//        creatable false;
//        returnedByDefault false;
//        required false;
//        multiValued true
//        description "Types available in this project";
//    }

    //TODO complex type, relevant=false?, reference=true
//    attribute("versions") {
//        readable true;
//        updateable false;
//        creatable false;
//        returnedByDefault false;
//        required false;
//        multiValued true
//        description "Versions available in this project";
//    }

    //TODO complex type, reference=true
//    attribute("memberships") {
//        readable true;
//        updateable false;
//        creatable false;
//        returnedByDefault false;
//        required false;
//        multiValued true
        description "Memberships in the project.";
//    }

    //TODO missing schema info, check docu ...
//    attribute("workPackages") {
//        multiValued true
//        description "Work Packages of this project";
//    }

    //TODO complex type, reference=true
//    attribute("parent") {
//        readable true;
//        updateable true;
//        creatable true;
//        returnedByDefault true;
//        required false;
        description "Parent project of the project";
//    }

    //TODO complex type, relevant=false?, reference=true
//    attribute("status") {
//        readable true;
//        updateable true;
//        creatable true;
//        returnedByDefault true;
//        required false;
//        description "Denotes the status of the project, so whether the project is on track, at risk or is having trouble.";
//    }
}