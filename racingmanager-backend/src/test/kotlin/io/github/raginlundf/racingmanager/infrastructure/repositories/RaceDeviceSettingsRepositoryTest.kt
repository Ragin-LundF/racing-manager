package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.FinishSemantics
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RaceDeviceSettingsRepositoryTest {

    private val repository = RaceDeviceSettingsRepository()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `find returns null when nothing has been saved`() {
        assertNull(actual = repository.find())
    }

    @Test
    fun `save then find round-trips the settings`() {
        val settings = RaceDeviceSettings(
            mode = RaceDeviceMode.HARDWARE,
            endpoint = "ws://192.168.1.50:8080/race",
            finishTimeoutMs = 45_000,
        )

        repository.save(settings = settings)

        val found = repository.find()
        assertNotNull(actual = found)
        assertEquals(expected = settings, actual = found)
    }

    @Test
    fun `save then find round-trips the arduino serial options`() {
        val settings = RaceDeviceSettings(
            mode = RaceDeviceMode.ARDUINO_TWO_LANE,
            endpoint = "ws://unused",
            finishTimeoutMs = 60_000,
            arduino = ArduinoTwoLaneSettings(
                portName = "/dev/ttyACM0",
                baudRate = 57_600,
                readyTimeoutMs = 8_000,
                falseStartWindowMs = 400,
                finishSemantics = FinishSemantics.ELAPSED,
                rawLogPath = "logs/raw-timing.log",
            ),
        )

        repository.save(settings = settings)

        assertEquals(expected = settings, actual = repository.find())
    }

    @Test
    fun `the arduino options are absent for the websocket modes`() {
        repository.save(
            settings = RaceDeviceSettings(
                mode = RaceDeviceMode.SIMULATED,
                endpoint = "ws://test",
                finishTimeoutMs = 30_000,
            ),
        )

        assertNull(actual = repository.find()?.arduino)
    }

    @Test
    fun `save replaces the single row rather than appending`() {
        repository.save(
            settings = RaceDeviceSettings(
                mode = RaceDeviceMode.SIMULATED,
                endpoint = "ws://old",
                finishTimeoutMs = 30_000
            ),
        )
        val updated = RaceDeviceSettings(
            mode = RaceDeviceMode.HARDWARE,
            endpoint = "ws://new:8080/race",
            finishTimeoutMs = 20_000
        )

        repository.save(settings = updated)

        // find() uses singleOrNull(): it would throw if a second row had been
        // appended, so a successful read proves the single-row invariant holds.
        assertEquals(expected = updated, actual = repository.find())
    }
}
