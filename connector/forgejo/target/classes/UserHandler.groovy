objectClass("User") {
    search {
        operation "GET"
        endpoint "/users/"
        filter("id", EQUAL) {
            endpoint {
                return "/users/search?id=$value"
            }
        }
        filter("login",EQUAL) {
            endpoint {
                return "/users/$value"
            }
        }


    }
}