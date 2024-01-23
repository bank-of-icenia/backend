package sh.okx.bankoficenia.backend.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CustomDateTimeFunction
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import sh.okx.bankoficenia.backend.model.UserSession
import java.security.SecureRandom
import javax.sql.DataSource

val csprng = SecureRandom()

data class SqlSessionDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS sessions (\"id\" VARCHAR(64) PRIMARY KEY, csrf VARCHAR(64), expiration TIMESTAMP, user_id BIGINT)")
        }
    }

    fun read(id: String): UserSession? {
        return transaction {
            val session =
                Sessions.select { (Sessions.sessionId eq id) and (Sessions.expiration greater CustomDateTimeFunction("NOW")) }
                    .singleOrNull()
            if (session != null) {
                return@transaction UserSession(session[Sessions.userId], session[Sessions.csrf])
            } else {
                return@transaction null
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun write(id: String, user: Long) {
        dataSource.connection.use {
            val stmt = it.prepareStatement("INSERT INTO sessions VALUES (?, ?, NOW() + INTERVAL '7 DAYS', ?) ON CONFLICT (\"id\") DO UPDATE SET expiration = NOW() + INTERVAL '7 DAYS', csrf = ?, user_id = ?")
            stmt.setString(1, id)
            val bytes = ByteArray(32)
            csprng.nextBytes(bytes)
            val csrf = bytes.toHexString()
            stmt.setString(2, csrf)
            stmt.setLong(3, user)
            stmt.setString(4, csrf)
            stmt.setLong(5, user)
            stmt.executeUpdate()
        }
    }

    fun invalidate(id: String) {
        return transaction {
            Sessions.deleteWhere { sessionId eq id }
        }
    }
}

object Sessions : Table("sessions") {
    val sessionId = varchar("id", 64)
    val csrf = varchar("csrf", 64)
    val expiration = datetime("expiration")
    val userId = long("user_id")

    override val primaryKey = PrimaryKey(sessionId)
}