package io.github.raginlundf.racingmanager.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeploymentModeTest {
    @Test
    fun `parses local case-insensitively`() {
        assertEquals(expected = DeploymentMode.LOCAL, actual = DeploymentMode.from("local"))
        assertEquals(expected = DeploymentMode.LOCAL, actual = DeploymentMode.from("Local"))
        assertEquals(expected = DeploymentMode.LOCAL, actual = DeploymentMode.from("LOCAL"))
    }

    @Test
    fun `parses hosted case-insensitively`() {
        assertEquals(expected = DeploymentMode.HOSTED, actual = DeploymentMode.from("hosted"))
        assertEquals(expected = DeploymentMode.HOSTED, actual = DeploymentMode.from("HOSTED"))
    }

    @Test
    fun `rejects unknown mode values`() {
        assertFailsWith<IllegalStateException> { DeploymentMode.from("cloud") }
    }
}
