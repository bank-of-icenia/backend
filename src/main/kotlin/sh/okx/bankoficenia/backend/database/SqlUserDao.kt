package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.User
import javax.sql.DataSource

data class SqlUserDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "\"id\" BIGSERIAL," +
                            "ign TEXT UNIQUE," +
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
                        resultSet.getString("ign"),
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
                resultSet.getString("ign"),
                resultSet.getLong("discord_id"),
                resultSet.getString("discord_username"),
                resultSet.getString("discord_globalname"),
                resultSet.getBoolean("admin"),
                resultSet.getTimestamp("registered").toLocalDateTime()
            )
        }
    }

    fun updateIgn(id: Long, ign: String): User? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("UPDATE users SET ign = ? WHERE \"id\" = ? RETURNING *")
            stmt.setString(1, ign)
            stmt.setLong(2, id)
            val resultSet = stmt.executeQuery()
            if (!resultSet.next()) {
                return null
            }

            return User(
                resultSet.getLong("id"),
                resultSet.getString("ign"),
                resultSet.getLong("discord_id"),
                resultSet.getString("discord_username"),
                resultSet.getString("discord_globalname"),
                resultSet.getBoolean("admin"),
                resultSet.getTimestamp("registered").toLocalDateTime()
            )
        }
    }

    fun createUser(discordId: Long, ign: String): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement(
                "INSERT INTO users (discord_id, ign) " +
                        "VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING " +
                        "RETURNING id"
            )
            stmt.setLong(1, discordId)
            stmt.setString(2, ign)
            val resultSet = stmt.executeQuery()
            if (!resultSet.next()) {
                return null
            }

            return resultSet.getLong("id")
        }
    }

    fun getOrCreateUser(discordId: Long, discordUsername: String, discordGlobalname: String?): Long? {
        dataSource.connection.use {
            val stmt = it.prepareStatement(
                "INSERT INTO users (discord_id, discord_username, discord_globalname) " +
                        "VALUES (?, ?, ?) " +
                        "ON CONFLICT (discord_id) DO UPDATE SET discord_id = ?, discord_username = ?, discord_globalname = ? " +
                        "RETURNING id"
            )
            stmt.setLong(1, discordId)
            stmt.setString(2, discordUsername)
            stmt.setString(3, discordGlobalname)
            stmt.setLong(4, discordId)
            stmt.setString(5, discordUsername)
            stmt.setString(6, discordGlobalname)
            val resultSet = stmt.executeQuery()
            if (!resultSet.next()) {
                return null
            }

            return resultSet.getLong("id")
        }
    }
}