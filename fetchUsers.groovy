import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET

def fetchUsers() {
    def http = new HTTPBuilder('https://scim.dev/api/Users')

    http.request(GET, JSON) { req ->
        headers.'Content-Type' = 'application/scim+json'

        response.success = { resp, json ->
            println "Fetched ${json.Resources.size()} users"
            return json.Resources
        }

        response.failure = { resp ->
            println "Failed to fetch users: ${resp.statusLine}"
        }
    }
}

// Call the function to fetch users
fetchUsers()
