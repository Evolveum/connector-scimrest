/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Organization") {
    connIdAttribute("UID", "name");
    connIdAttribute("NAME", "username");
}