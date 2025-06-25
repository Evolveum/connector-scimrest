/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package nextCloud

objectClass("App") {
    attribute("filter") {
        jsonType "string";
        description "optional (enabled or disabled)";
    }
}