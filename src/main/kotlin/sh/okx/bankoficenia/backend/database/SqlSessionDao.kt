package sh.okx.bankoficenia.backend.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CustomDateTimeFunction
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import sh.okx.bankoficenia.backend.model.User
import java.time.LocalDateTime
import java.util.NoSuchElementException
import javax.sql.DataSource

data class SqlSessionDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS sessions (\"id\" VARCHAR(64) PRIMARY KEY, expiration TIMESTAMP, user_id BIGINT)")
        }
    }

    fun read(id: String): Long? {
        return transaction {
            val session =
                Sessions.select { (Sessions.sessionId eq id) and (Sessions.expiration greater CustomDateTimeFunction("NOW")) }
                    .singleOrNull()
            if (session != null) {
                return@transaction session[Sessions.userId]
            } else {
                return@transaction null
            }
        }
    }

    fun write(id: String, user: Long) {
        dataSource.connection.use {
            val stmt = it.prepareStatement("INSERT INTO sessions VALUES (?, NOW() + INTERVAL '7 DAYS', ?) ON CONFLICT (\"id\") DO UPDATE SET expiration = NOW() + INTERVAL '7 DAYS', user_id = ?")
            stmt.setString(1, id)
            stmt.setLong(2, user)
            stmt.setLong(3, user)
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
    val expiration = datetime("expiration")
    val userId = long("user_id")

    override val primaryKey = PrimaryKey(sessionId)
}

class DateAddDays(val dateExp: Expression<LocalDateTime?>, val addDays: Expression<Int>) : Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("DATE_ADD(", dateExp, ", INTERVAL ", addDays, " DAY)")
    }
}