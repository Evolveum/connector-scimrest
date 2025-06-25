/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    /** Mapping from native attributes to connID attributes **/
    connIdAttribute("UID", "id");
    connIdAttribute("NAME", "login");

    /*
    connIdAttribute "ENABLE" "active";
    connIdAttribute "LAST_LOGIN_DATE" "last_login_date";
    connIdAttribute "LOCK_OUT" "prohibit_login";
    connIdAttribute "SHORT_NAME" "full_name";
    connIdAttribute "DESCRIPTION" "description";
    */
}
