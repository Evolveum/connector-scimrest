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

objectClass("Organization") {
    connIdAttribute("UID", "id");
    connIdAttribute("NAME", "name");
}