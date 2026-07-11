package io.github.raginlundf.racingmanager.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseTestHelper {

    private var dataSource: HikariDataSource? = null

    fun setUp() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:file:test-${System.nanoTime()}.db?mode=memory&cache=shared"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
        }
        dataSource = HikariDataSource(config)
        ExposedDatabase.connect(dataSource!!)

        val connection = dataSource!!.connection
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

    fun tearDown() {
        transaction {
            exec("DROP TABLE IF EXISTS measurements")
            exec("DROP TABLE IF EXISTS heat_lanes")
            exec("DROP TABLE IF EXISTS heats")
            exec("DROP TABLE IF EXISTS knockout_matches")
            exec("DROP TABLE IF EXISTS knockout_tournaments")
            exec("DROP TABLE IF EXISTS qualifications")
            exec("DROP TABLE IF EXISTS event_seeds")
            exec("DROP TABLE IF EXISTS vehicles")
            exec("DROP TABLE IF EXISTS participants")
            exec("DROP TABLE IF EXISTS events")
            exec("DROP TABLE IF EXISTS audit_entries")
            exec("DROP TABLE IF EXISTS sessions")
            exec("DROP TABLE IF EXISTS users")
            exec("DROP TABLE IF EXISTS DATABASECHANGELOG")
            exec("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK")
        }
        dataSource?.close()
        dataSource = null
    }
}
