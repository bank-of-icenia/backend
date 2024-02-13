package sh.okx.bankoficenia.backend.database

import javax.sql.DataSource
import sh.okx.bankoficenia.backend.model.User
import sh.okx.bankoficenia.backend.then

val IGN_COLUMN = Column.ofString("ign")

data class SqlUserDao(
    val dataSource: DataSource
) {
    init {
        dataSource.connection.use {
            it.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL,
                    ign TEXT UNIQUE,
                    discord_id BIGINT UNIQUE,
                    discord_username TEXT,
                    discord_globalname TEXT,
                    admin BOOL NOT NULL DEFAULT FALSE,
                    registered TIMESTAMP DEFAULT NOW(),
                    PRIMARY KEY ("id")
                );
            """.trimIndent())
        }
    }

    fun getAllUsers(): List<User> {
        dataSource.connection.use {
            return ArrayList<User>().apply {
                val results = it.prepareStatement("""SELECT * FROM users ORDER BY "id";""").executeQuery()
                while (results.next()) {
                    add(User(
                        results.getLong("id"),
                        results.getString("ign"),
                        results.getLong("discord_id"),
                        results.getString("discord_username"),
                        results.getString("discord_globalname"),
                        results.getBoolean("admin"),
                        results.getTimestamp("registered").toLocalDateTime()
                    ))
                }
            }
        }
    }

    fun getUserById(
        id: Long
    ): User? {
        dataSource.connection.use {
            val result = it.prepareStatement("""SELECT * FROM users WHERE "id" = ?;""").then {
                setLong(1, id)
                executeQuery()
            }
            if (result.next()) {
                return User(
                    result.getLong("id"),
                    result.getString("ign"),
                    result.getLong("discord_id"),
                    result.getString("discord_username"),
                    result.getString("discord_globalname"),
                    result.getBoolean("admin"),
                    result.getTimestamp("registered").toLocalDateTime()
                )
            }
            return null
        }
    }

    fun hasIgn(
        ign: String
    ): Boolean {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT 1 FROM users WHERE ign = ?")
            stmt.setString(1, ign)
            val resultSet = stmt.executeQuery()
            return resultSet.next()
        }
    }

    fun updateUserById(
        id: Long,
        what: UpdateTargets
    ): User? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("""
                UPDATE users
                SET
                    ${what.asColumns()}
                WHERE
                    "id" = ?
                RETURNING *;
            """.trimIndent())
            var index = what.setParams(stmt)
            stmt.setLong(++index, id)
            val result = stmt.executeQuery()
            if (result.next()) {
                return User(
                    result.getLong("id"),
                    result.getString("ign"),
                    result.getLong("discord_id"),
                    result.getString("discord_username"),
                    result.getString("discord_globalname"),
                    result.getBoolean("admin"),
                    result.getTimestamp("registered").toLocalDateTime()
                )
            }
            return null
        }
    }

    fun createUser(
        discordId: Long,
        ign: String
    ): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("""
                INSERT INTO users
                    (discord_id, ign)
                VALUES
                    (?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id;
            """.trimIndent())
            stmt.setLong(1, discordId)
            stmt.setString(2, ign)
            val results = stmt.executeQuery()
            if (results.next()) {
                return results.getLong("id")
            }
            return null
        }
    }

    fun getOrCreateUser(
        discordId: Long,
        discordUsername: String,
        discordGlobalname: String?
    ): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("""
                INSERT INTO users
                    (discord_id, discord_username, discord_globalname)
                VALUES
                    (?, ?, ?)
                ON CONFLICT
                    (discord_id)
                DO UPDATE SET
                    discord_username = ?,
                    discord_globalname = ?
                RETURNING id;
            """.trimIndent())
            stmt.setLong(1, discordId)
            stmt.setString(2, discordUsername)
            stmt.setString(3, discordGlobalname)
            stmt.setString(4, discordUsername)
            stmt.setString(5, discordGlobalname)
            val results = stmt.executeQuery()
            if (results.next()) {
                return results.getLong("id")
            }
            return null
        }
    }
}
