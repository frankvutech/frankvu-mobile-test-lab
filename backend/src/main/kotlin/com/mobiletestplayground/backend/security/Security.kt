package com.mobiletestplayground.backend.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.mobiletestplayground.backend.config.AppConfig
import com.mobiletestplayground.backend.domain.Role
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 password hashing from the JDK, stored as
 * `pbkdf2_sha256$<iterations>$<salt b64>$<hash b64>`. Plain-text passwords are never stored.
 */
class PasswordHasher(private val iterations: Int = DEFAULT_ITERATIONS) {
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, iterations)
        val encoder = Base64.getEncoder()
        return "$PREFIX$$iterations$${encoder.encodeToString(salt)}$${encoder.encodeToString(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != PARTS || parts[0] != PREFIX) return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[2])
        val expected = decoder.decode(parts[3])
        return MessageDigest.isEqual(expected, derive(password, salt, rounds))
    }

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PREFIX = "pbkdf2_sha256"
        const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val PARTS = 4
    }
}

data class IssuedToken(val token: String, val tokenId: String, val expiresAtEpochSeconds: Long)

/** Signs and verifies HS256 bearer tokens. Each token carries a unique id (`jti`) so logout can revoke it. */
class TokenService(private val config: AppConfig, private val clock: Clock = Clock.systemUTC()) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(config.jwtIssuer).withAudience(AUDIENCE).build()

    fun issue(userId: String, role: Role): IssuedToken {
        val now = clock.instant()
        val expires = now.plusSeconds(config.tokenTtlSeconds)
        val tokenId = UUID.randomUUID().toString()
        val token = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .withClaim(ROLE_CLAIM, role.name)
            .withJWTId(tokenId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expires))
            .sign(algorithm)
        return IssuedToken(token, tokenId, expires.epochSecond)
    }

    companion object {
        const val AUDIENCE = "mobile-test-playground-api"
        const val ROLE_CLAIM = "role"
        const val REALM = "mobile-test-playground"
    }
}
