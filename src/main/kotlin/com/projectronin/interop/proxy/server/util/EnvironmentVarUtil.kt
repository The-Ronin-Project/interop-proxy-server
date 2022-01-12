package com.projectronin.interop.proxy.server.util

/**
 * Loads a secret from the environment variable named [key], cleans it up by removing any headers, footers, blank
 * spaces or line separators, and returns it.  If the env variable hasn't been set in the current environment it
 * returns null. Might be worth moving into interop-common if it's something we can use in other places.
 */
fun getKeyFromEnv(key: String): String? {
    return try {
        System.getenv(key)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(" ", "")
            .replace(System.lineSeparator(), "")
    } catch (e: Exception) {
        null
    }
}
