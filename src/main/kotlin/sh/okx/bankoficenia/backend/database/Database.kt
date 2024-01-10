package sh.okx.bankoficenia.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*

fun getDataSource(applicationConfig: ApplicationConfig): HikariDataSource {
    val config = HikariConfig()
    config.jdbcUrl = "jdbc:postgresql://localhost:${
        applicationConfig.property("port").getString()
    }/${applicationConfig.property("database").getString()}"
    config.username = applicationConfig.property("username").getString()
    config.password = applicationConfig.property("password").getString()
    return HikariDataSource(config)
}