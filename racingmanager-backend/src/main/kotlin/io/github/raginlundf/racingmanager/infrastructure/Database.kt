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
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

fun Application.configureDatabase(): javax.sql.DataSource {
    val url = environment.config.propertyOrNull("racingmanager.database.url")?.getString()

    // A JDBC URL (e.g. MariaDB) switches away from the embedded SQLite default.
    // Exposed auto-detects the dialect from the connection and Liquibase is
    // DB-agnostic, so only the datasource assembly differs between the two.
    val hikariConfig = if (url != null) {
        configureExternalDatabase(url)
    } else {
        configureSqliteDatabase()
    }.apply {
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 30_000
        connectionTimeout = 10_000
        isAutoCommit = false
    }

    val dataSource = HikariDataSource(hikariConfig)

    ExposedDatabase.connect(dataSource)

    runLiquibaseMigration(dataSource)

    logger.info { "Database initialized (${hikariConfig.jdbcUrl.substringBefore('?')})" }
    return dataSource
}

private fun Application.configureSqliteDatabase(): HikariConfig {
    val dbPath = environment.config.propertyOrNull("racingmanager.database.path")
        ?.getString()
        ?: resolveDefaultDatabasePath()

    val dbDir = Path.of(dbPath).parent
    if (dbDir != null) {
        Files.createDirectories(dbDir)
    }

    val dbFile = Path.of(dbPath)
    if (Files.exists(dbFile)) {
        createPreMigrationBackup(dbFile)
    }

    return HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:$dbPath?journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000"
        driverClassName = "org.sqlite.JDBC"
    }
}

private fun Application.configureExternalDatabase(url: String): HikariConfig {
    // Credentials come from the environment for hosted deployments, never files.
    val user = environment.config.propertyOrNull("racingmanager.database.user")?.getString()
    val password = environment.config.propertyOrNull("racingmanager.database.password")?.getString()

    return HikariConfig().apply {
        jdbcUrl = url
        driverClassName = driverClassNameFor(url)
        if (user != null) username = user
        if (password != null) this.password = password
    }
}

private fun driverClassNameFor(url: String): String = when {
    url.startsWith("jdbc:mariadb:") -> "org.mariadb.jdbc.Driver"
    url.startsWith("jdbc:sqlite:") -> "org.sqlite.JDBC"
    else -> error("Unsupported jdbc url '$url': only jdbc:mariadb: and jdbc:sqlite: are supported")
}

private fun createPreMigrationBackup(dbFile: Path) {
    val backupDir = dbFile.parent.resolve("backups")
    Files.createDirectories(backupDir)
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val backupFile = backupDir.resolve("racingmanager-pre-migration-$timestamp.db")
    Files.copy(dbFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
    logger.info { "Pre-migration backup created at $backupFile" }
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
