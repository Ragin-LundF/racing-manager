package io.github.raginlundf.racingmanager.infrastructure.security

import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalJwtKeyProviderTest {

    private val repository = SigningKeyRepository()
    private val provider = LocalJwtKeyProvider(repository)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `ensureKeyExists generates a key on first call`() {
        val key = provider.ensureKeyExists()

        assertTrue(key.active)
        assertEquals(expected = "RS256", actual = key.algorithm)
        assertNotNull(key.privateKey)
    }

    @Test
    fun `ensureKeyExists is idempotent`() {
        val first = provider.ensureKeyExists()
        val second = provider.ensureKeyExists()

        assertEquals(expected = first.kid, actual = second.kid)
    }

    @Test
    fun `signingKey round-trips through persistence by kid`() {
        val generated = provider.ensureKeyExists()

        val signing = provider.signingKey()
        val reloaded = assertIs<SigningKey.Rsa>(LocalJwtKeyProvider(SigningKeyRepository()).signingKey())

        assertEquals(expected = generated.kid, actual = signing.kid)
        assertEquals(expected = generated.publicKey, actual = reloaded.publicKey)
        assertEquals(expected = generated.privateKey, actual = reloaded.privateKey)
    }

    @Test
    fun `signingKey fails fast when no key has been generated`() {
        assertFailsWith<IllegalStateException> { provider.signingKey() }
    }

    @Test
    fun `verificationKey finds a key by kid regardless of active flag`() {
        val original = provider.ensureKeyExists()
        val rotated = provider.rotate()

        val stillVerifiable = provider.verificationKey(original.kid)

        assertNotNull(stillVerifiable)
        assertEquals(expected = original.kid, actual = stillVerifiable.kid)
        assertNotEquals(illegal = rotated.kid, actual = stillVerifiable.kid)
    }

    @Test
    fun `rotate deactivates the previous key and signs with the new one`() {
        val original = provider.ensureKeyExists()
        val rotated = provider.rotate()

        assertNotEquals(illegal = original.kid, actual = rotated.kid)
        assertTrue(rotated.active)
        assertEquals(expected = rotated.kid, actual = provider.signingKey().kid)

        val previous = provider.verificationKey(original.kid)
        assertNotNull(previous)
        assertTrue(!previous.active)
    }
}
