package com.mobiletestplayground.backend.config

/**
 * Runtime configuration, read from environment variables (see backend/.env.example).
 *
 * Defaults give a self-contained local server: an H2 database file under ./data, test support
 * enabled, and a development-only JWT secret. Every value can be overridden, e.g. to point at the
 * PostgreSQL service in docker-compose.yml.
 */
data class AppConfig(
    val port: Int = 8080,
    val jdbcUrl: String = "jdbc:h2:file:./data/playground;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    val dbUser: String = "sa",
    val dbPassword: String = "",
    val jwtSecret: String = DEV_ONLY_SECRET,
    val jwtIssuer: String = "mobile-test-playground",
    val tokenTtlSeconds: Long = 3_600,
    /** Enables POST /api/v1/test/reset and the deterministic error endpoints. Never enable in a shared deployment. */
    val testSupportEnabled: Boolean = true,
) {
    companion object {
        /** Local development only. Override with JWT_SECRET anywhere else. */
        const val DEV_ONLY_SECRET = "local-development-only-secret-change-me"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val defaults = AppConfig()
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: defaults.port,
                jdbcUrl = env["DATABASE_URL"] ?: defaults.jdbcUrl,
                dbUser = env["DATABASE_USER"] ?: defaults.dbUser,
                dbPassword = env["DATABASE_PASSWORD"] ?: defaults.dbPassword,
                jwtSecret = env["JWT_SECRET"] ?: defaults.jwtSecret,
                jwtIssuer = env["JWT_ISSUER"] ?: defaults.jwtIssuer,
                tokenTtlSeconds = env["TOKEN_TTL_SECONDS"]?.toLongOrNull() ?: defaults.tokenTtlSeconds,
                testSupportEnabled = env["TEST_SUPPORT_ENABLED"]?.toBooleanStrictOrNull() ?: defaults.testSupportEnabled,
            )
        }

        /** An isolated in-memory database per name, for tests. */
        fun inMemory(name: String) = AppConfig(
            jdbcUrl = "jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        )
    }
}
