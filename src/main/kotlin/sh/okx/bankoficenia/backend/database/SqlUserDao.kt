package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.User
import javax.sql.DataSource
import kotlin.collections.ArrayList

data class SqlUserDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "\"id\" BIGSERIAL," +
                            "discord_id BIGINT UNIQUE," +
                            "discord_username TEXT," +
                            "discord_globalname TEXT," +
                            "admin BOOL NOT NULL DEFAULT FALSE," +
                            "registered TIMESTAMP DEFAULT NOW()," +
                            "PRIMARY KEY (\"id\"))"
                )
        }
    }

    fun getUsers(): List<User> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users ORDER BY \"id\"")
            val users = ArrayList<User>()

            val resultSet = stmt.executeQuery()
            while (resultSet.next()) {
                users.add(
                    User(
                        resultSet.getLong("id"),
                        resultSet.getLong("discord_id"),
                        resultSet.getString("discord_username"),
                        resultSet.getString("discord_globalname"),
                        resultSet.getBoolean("admin"),
                        resultSet.getTimestamp("registered").toLocalDateTime()
                    )
                )
            }

            return users
        }
    }

    fun read(id: Long): User? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users WHERE \"id\" = ?")
            stmt.setLong(1, id)
            val resultSet = stmt.executeQuery()
            if (!resultSet.next()) {
                return null
            }

            return User(
                resultSet.getLong("id"),
                resultSet.getLong("discord_id"),
                resultSet.getString("discord_username"),
                resultSet.getString("discord_globalname"),
                resultSet.getBoolean("admin"),
                resultSet.getTimestamp("registered").toLocalDateTime()
            )
        }
    }

    fun createUser(discordId: Long): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement(
                "INSERT INTO users (discord_id) " +
                        "VALUES (?) " +
                        "ON CONFLICT DO NOTHING " +
                        "RETURNING id"
            )
            stmt.setLong(1, discordId)
            val resultSet = stmt.executeQuery()
            if (!resultSet.next()) {
                return null
            }

            return resultSet.getLong("id")
        }
    }

    fun getOrCreateUser(discordId: Long, discordUsername: String, discordGlobalname: String): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement(
                "INSERT INTO users (discord_id, discord_username, discord_globalname) " +
                        "VALUES (?, ?, ?) " +
                        "ON CONFLICT DO UPDATE SET discord_id = ?, discord_username = ?, discord_globalname = ? " +
                        "RETURNING id"
            )
            stmt.setLong(1, discordId)
            stmt.setString(2, discordUsername)
            stmt.setString(3, discordGlobalname)
            stmt.setLong(4, discordId)
            stmt.setString(5, discordUsername)
            stmt.setString(6, discordGlobalname)
            val resultSet = stmt.executeQuery()
            // TODO test that commenting this out is fine
//            if (!resultSet.next()) {
//                val stmt2 = it.prepareStatement("SELECT \"id\" FROM users WHERE discord_id = ?")
//                stmt2.setLong(1, discordId)
//                val resultSet2 = stmt2.executeQuery()
//                if (!resultSet2.next()) {
//                    return null
//                }
//
//                return resultSet2.getLong("id")
//            }

            return resultSet.getLong("id")
        }
    }
}