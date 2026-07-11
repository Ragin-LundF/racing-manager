package com.example.racingmanager.application.auth

import com.example.racingmanager.domain.audit.AuditEntryEntity
import com.example.racingmanager.domain.session.SessionEntity
import com.example.racingmanager.domain.user.UserEntity
import com.example.racingmanager.domain.user.UserRole
import com.example.racingmanager.infrastructure.repositories.AuditRepository
import com.example.racingmanager.infrastructure.repositories.SessionRepository
import com.example.racingmanager.infrastructure.repositories.UserRepository
import com.example.racingmanager.infrastructure.security.PasswordHasher
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionDuration: Duration = 8.hours,
    private val idleTimeout: Duration = 30.minutes,
) {
    private val clock: Clock = Clock.System

    fun isFirstRun(): Boolean = userRepository.count() == 0L

    fun setupAdmin(
        username: String,
        password: String,
        displayName: String,
    ): SetupResult {
        if (!isFirstRun()) return SetupResult.AlreadySetup

        val passwordHash = passwordHasher.hash(password)
        val user = UserEntity(
            id = UUID.randomUUID(),
            username = username,
            passwordHash = passwordHash,
            displayName = displayName,
            role = UserRole.ADMIN,
            createdAt = clock.now(),
        )
        userRepository.insert(user)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "SETUP_COMPLETED",
                targetType = "User",
                targetId = user.id,
                summary = "First-run setup completed by ${user.username}",
                occurredAt = clock.now(),
            ),
        )
        return SetupResult.Success(user)
    }

    fun login(
        username: String,
        password: String,
        correlationId: String? = null,
    ): LoginResult {
        val user = userRepository.findByUsername(username)
            ?: return LoginResult.InvalidCredentials

        if (!passwordHasher.verify(password, user.passwordHash)) {
            auditRepository.insert(
                AuditEntryEntity(
                    id = UUID.randomUUID(),
                    actorId = user.id,
                    action = "LOGIN_FAILED",
                    targetType = "User",
                    targetId = user.id,
                    correlationId = correlationId,
                    occurredAt = clock.now(),
                ),
            )
            return LoginResult.InvalidCredentials
        }

        val now = clock.now()
        val session = SessionEntity(
            id = UUID.randomUUID(),
            userId = user.id,
            createdAt = now,
            expiresAt = now.plus(sessionDuration),
            lastAccessedAt = now,
        )
        sessionRepository.insert(session)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "LOGIN_SUCCESS",
                targetType = "Session",
                targetId = session.id,
                correlationId = correlationId,
                occurredAt = now,
            ),
        )
        return LoginResult.Success(session, user)
    }

    fun getSession(sessionId: UUID): SessionResult {
        val session = sessionRepository.findById(sessionId)
            ?: return SessionResult.NotFound

        val now = clock.now()
        if (session.isExpired(now)) {
            sessionRepository.deleteById(sessionId)
            return SessionResult.Expired
        }

        if (now > session.lastAccessedAt.plus(idleTimeout)) {
            sessionRepository.deleteById(sessionId)
            return SessionResult.Expired
        }

        sessionRepository.updateLastAccessed(sessionId, now)
        val user = userRepository.findById(session.userId)
            ?: return SessionResult.NotFound

        return SessionResult.Valid(session, user)
    }

    fun logout(sessionId: UUID, correlationId: String? = null) {
        val session = sessionRepository.findById(sessionId)
        sessionRepository.deleteById(sessionId)
        if (session != null) {
            auditRepository.insert(
                AuditEntryEntity(
                    id = UUID.randomUUID(),
                    actorId = session.userId,
                    action = "LOGOUT",
                    targetType = "Session",
                    targetId = sessionId,
                    correlationId = correlationId,
                    occurredAt = clock.now(),
                ),
            )
        }
    }

    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
    ): ChangePasswordResult {
        val user = userRepository.findById(userId)
            ?: return ChangePasswordResult.UserNotFound

        if (!passwordHasher.verify(currentPassword, user.passwordHash)) {
            return ChangePasswordResult.InvalidCurrentPassword
        }

        val newHash = passwordHasher.hash(newPassword)
        // TODO: update password hash in repository
        sessionRepository.deleteByUserId(userId)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userId,
                action = "PASSWORD_CHANGED",
                targetType = "User",
                targetId = userId,
                occurredAt = clock.now(),
            ),
        )
        return ChangePasswordResult.Success
    }
}

sealed interface SetupResult {
    data class Success(val user: UserEntity) : SetupResult
    data object AlreadySetup : SetupResult
}

sealed interface LoginResult {
    data class Success(val session: SessionEntity, val user: UserEntity) : LoginResult
    data object InvalidCredentials : LoginResult
}

sealed interface SessionResult {
    data class Valid(val session: SessionEntity, val user: UserEntity) : SessionResult
    data object Expired : SessionResult
    data object NotFound : SessionResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object UserNotFound : ChangePasswordResult
    data object InvalidCurrentPassword : ChangePasswordResult
}
