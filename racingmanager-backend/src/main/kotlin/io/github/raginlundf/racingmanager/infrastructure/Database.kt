package io.github.raginlundf.racingmanager.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun Application.configureDatabase() {
    val dbPath = environment.config.propertyOrNull("racingmanager.database.path")
        ?.getString()
        ?: resolveDefaultDatabasePath()

    val dbDir = Path.of(dbPath).parent
    if (dbDir != null) {
        Files.createDirectories(dbDir)
    }

    val hikariConfig = HikariConfig().apply {
        // WAL lets readers run concurrently with the single writer, and busy_timeout
        // makes SQLite wait for a lock instead of failing fast — together they remove
        // the request-serialization stalls that made list endpoints feel slow.
        jdbcUrl = "jdbc:sqlite:$dbPath?journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000"
        driverClassName = "org.sqlite.JDBC"
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 30_000
        connectionTimeout = 10_000
        isAutoCommit = false
    }

    val dataSource = HikariDataSource(hikariConfig)

    ExposedDatabase.connect(dataSource)

    runLiquibaseMigration(dataSource)

    logger.info { "Database initialized at $dbPath" }
}

private fun runLiquibaseMigration(dataSource: HikariDataSource) {
    val connection = dataSource.connection
    try {
        val database = JdbcConnection(connection)
        val liquibase = Liquibase(
            "db/changelog/db.changelog-master.xml",
            ClassLoaderResourceAccessor(),
            database,
        )
        liquibase.update()
    } finally {
        connection.close()
    }
}

private fun resolveDefaultDatabasePath(): String {
    val appDir = Path.of(
        System.getProperty("user.home"),
        ".racingmanager",
    )
    Files.createDirectories(appDir)
    return appDir.resolve("racingmanager.db").toString()
}
