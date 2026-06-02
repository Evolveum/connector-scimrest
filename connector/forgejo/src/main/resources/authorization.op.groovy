authentication {
    rest {
        bearer {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
        apiKey {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
    }
}
