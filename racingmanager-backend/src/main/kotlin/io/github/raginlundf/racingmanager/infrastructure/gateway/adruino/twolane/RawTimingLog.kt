package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/** Appends every line sent to and received from the board, verbatim and with a host
    timestamp. Mandatory: while the meaning of the `FINISH` value and of `RUN` are
    unresolved, this file is the only way to re-evaluate a race afterwards without
    running it again (`.plan/Adruino-impl.md` §6.3). A failing write must never break
    a race, so it is logged and swallowed. */
class RawTimingLog(private val path: Path) {
    private val writeLock = Mutex()

    constructor(path: String) : this(Path(path))

    suspend fun received(line: String) {
        append(direction = "IN", line = line)
    }

    suspend fun sent(line: String) {
        append(direction = "OUT", line = line)
    }

    private suspend fun append(direction: String, line: String) {
        val entry = "${Clock.System.now()} $direction $line"
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    path.createParentDirectories()
                    Files.writeString(path, "$entry\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                }.onFailure { logger.error(throwable = it) { "Cannot write raw timing log $path" } }
            }
        }
    }
}
