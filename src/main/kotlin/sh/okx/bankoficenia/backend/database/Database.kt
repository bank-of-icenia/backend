package sh.okx.bankoficenia.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import java.sql.PreparedStatement
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

fun constructDataSource(
    config: ApplicationConfig
): HikariDataSource {
    return HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:${config.property("database.port").getString()}/${config.property("database.database").getString()}"
        username = config.property("database.username").getString()
        password = config.property("database.password").getString()
    })
}

fun initDatabase() {
    transaction {
        // Interest
        SchemaUtils.create(InterestPaymentsTable)
        SchemaUtils.create(AccumulatedInterestTable)
    }
}

interface BankDao {
    val internal: Transaction

    fun abort()
}

@OptIn(ExperimentalContracts::class)
fun banking(
    block: BankDao.() -> Unit
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    transaction {
        val transaction = this

        block(object : BankDao {
            override val internal: Transaction
                get() = transaction

            override fun abort() {
                rollback()
            }
        })
    }
}

/**
 * This is to represent a column in a table in a more abstract way without going full ORM.
 * Use this in conjunction with [UpdateTargets], like so:
 * ```
 * // Defining the column
 * val IGN = Column.ofString("ign")
 *
 * // Using the column
 * userDao.updateUser(user.id, UpdateTargets(
 *     IGN.withValue(newIGN)
 * ))
 * ```
 */
abstract class Column<T> protected constructor(
    val columnName: String
) {
    protected abstract fun set(
        stmt: PreparedStatement,
        i: Int,
        value: T
    )

    class Value<T> internal constructor(
        val column: Column<T>,
        val setter: (PreparedStatement, Int) -> Unit
    )

    /** Set this column to the given value */
    fun withValue(
        value: T
    ): Value<T> {
        return Value(this, { stmt, i -> set(stmt, i, value) })
    }

    companion object {
        /** Represents a string column */
        fun ofString(
            name: String
        ): Column<String> {
            return object : Column<String>(name) {
                override fun set(stmt: PreparedStatement, i: Int, value: String) {
                    stmt.setString(i, value)
                }
            }
        }
    }
}


/**
 * This is meant as an easy way to update multiple columns at once without going full ORM.
 * ```
 * // Using UpdateTargets
 * userDao.updateUser(user.id, UpdateTargets(
 *     IGN.withValue(newIGN),
 *     DISCORD_GLOBAL_NAME.withValue(newDiscordGlobal)
 * ))
 *
 * // Interpreting the parameter
 * fun updateUser(id: Int, what: UpdateTargets) {
 *     connection.use {
 *         val stmt = it.prepareStatement("UPDATE users SET ${what.asColumns()} WHERE id = ?;")
 *         var index = what.setParams(stmt)
 *         stmt.setLong(++index, id)
 *         stmt.executeQuery()
 *     }
 * }
 * ```
 */
class UpdateTargets(
    vararg targets: Column.Value<out Any>
) {
    private val columns: Array<String>
    private val setters: Array<(PreparedStatement, Int) -> Unit>

    init {
        check(targets.isNotEmpty()) {
            "You have not specified any UPDATE targets!"
        }
        val targets = targets.reversed().distinctBy { it.column.columnName }.reversed().toTypedArray()
        this.columns = Array(targets.size) { i -> targets[i].column.columnName }
        this.setters = Array(targets.size) { i -> targets[i].setter }
    }

    fun asColumns(): String {
        return this.columns.joinToString(" AND ") {
            "\"${it}\" = ?"
        }
    }

    /**
     * Iterates through the targets, setting the parameters for each one.
     *
     * @return Returns the last index used. It should be incremented before being used again.
     */
    fun setParams(
        statement: PreparedStatement,
        initialIndex: Int = 0
    ): Int {
        var index = initialIndex
        this.setters.forEach { setter ->
            setter(statement, ++index)
        }
        return index
    }
}
