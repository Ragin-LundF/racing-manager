package io.github.raginlundf.racingmanager.infrastructure.security

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val maxAttempts: Int = 5,
    private val window: Duration = Duration.ofMinutes(1),
) {
    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()

    fun isAllowed(key: String): Boolean {
        val now = Instant.now()
        val recent = attempts.getOrPut(key) { mutableListOf() }
        synchronized(recent) {
            recent.removeAll { it.isBefore(now.minus(window)) }
            if (recent.size >= maxAttempts) return false
            recent.add(now)
            return true
        }
    }

    fun reset(key: String) {
        attempts.remove(key)
    }
}
