authorization {
    rest {
        tokenBased {
            request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
        }
        apiKey {
            request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
        }
    }
}