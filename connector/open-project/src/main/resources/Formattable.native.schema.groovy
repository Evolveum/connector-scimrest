/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Formattable") {
    embedded true
    description "Projects are containers structuring the information (e.g. work packages, wikis) into smaller groups."

    attribute("format") {
        jsonType "string";
        returnedByDefault true;
        description "Indicates the formatting language of the raw text"
    }
    attribute("raw") {
        jsonType "string"
        description "The raw text, as entered by the user"
        returnedByDefault true;
    }
    attribute("html") {
        jsonType "string"
        description "The text converted to HTML according to the format"
        returnedByDefault true
    }
}