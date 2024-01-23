package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.Account
import java.security.SecureRandom
import javax.sql.DataSource

data class SqlAccountDao(val dataSource: DataSource) {
    private var random = SecureRandom()

    init {
        dataSource.connection.use {
            // TODO add in game name as well?
            it.createStatement()
                .execute(
                    // There are two types of accounts:
                    // User accounts - which have a user_id and code, but not reference_name
                    // Admin accounts - which have a reference_name, but not a user_id or code. This means that they cannot be used as input in forms and cannot be transferred to.
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                            "\"id\" BIGSERIAL," +
                            "user_id BIGINT, " +
                            "reference_name TEXT, " +
                            "code VARCHAR(16) UNIQUE, " +
                            "name TEXT NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"), " +
                            "CHECK (reference_name IS NOT NULL OR (user_id IS NOT NULL AND code IS NOT NULL)))"
                );
        }
    }

    fun createAccount(userId: Long, name: String): Long? {
        dataSource.connection.use {
            for (i in 1..100) {
                val stmt =
                    it.prepareStatement("INSERT INTO accounts (user_id, code, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING RETURNING \"id\"")
                stmt.setLong(1, userId)

                val nos = random.nextInt(99 * 99)
                val left = nos / 99 + 1
                val right = nos % 99 + 1

                stmt.setString(2, String.format("01-%02d-%02d", left, right))
                stmt.setString(3, name)

                val resultSet = stmt.executeQuery()
                if (resultSet.next()) {
                    return resultSet.getLong("id")
                }
            }
            return null
        }
    }

    fun getAccounts(userId: Long): List<Account> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM accounts WHERE user_id = ? AND NOT closed")
            stmt.setLong(1, userId)

            val resultSet = stmt.executeQuery()
            val accounts = ArrayList<Account>()
            while (resultSet.next()) {
                val accUserId = resultSet.getLong("user_id")
                val accUserIdNull = resultSet.wasNull()
                accounts.add(Account(
                    resultSet.getLong("id"),
                    if (accUserIdNull) null else accUserId,
                    resultSet.getString("code"),
                    resultSet.getString("reference_name"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("closed")
                ))
            }
            return accounts
        }
    }

    fun read(accountId: Long): Account? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM accounts WHERE id = ? AND NOT closed")
            stmt.setLong(1, accountId)

            val resultSet = stmt.executeQuery()
            if (resultSet.next()) {
                val accUserId = resultSet.getLong("user_id")
                val accUserIdNull = resultSet.wasNull()
                return Account(
                    resultSet.getLong("id"),
                    if (accUserIdNull) null else accUserId,
                    resultSet.getString("code"),
                    resultSet.getString("reference_name"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("closed")
                )
            }
            return null
        }
    }

    fun readByCode(accountCode: String): Account? {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM accounts WHERE code = ? AND NOT closed")
            stmt.setString(1, accountCode)

            val resultSet = stmt.executeQuery()
            if (resultSet.next()) {
                val accUserId = resultSet.getLong("user_id")
                val accUserIdNull = resultSet.wasNull()
                return Account(
                    resultSet.getLong("id"),
                    if (accUserIdNull) null else accUserId,
                    resultSet.getString("code"),
                    resultSet.getString("reference_name"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("closed")
                )
            }
            return null
        }
    }

    fun getAllAccounts(): List<Account> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM accounts")

            val resultSet = stmt.executeQuery()
            val accounts = ArrayList<Account>()
            while (resultSet.next()) {
                val accUserId = resultSet.getLong("user_id")
                val accUserIdNull = resultSet.wasNull()
                accounts.add(Account(
                    resultSet.getLong("id"),
                    if (accUserIdNull) null else accUserId,
                    resultSet.getString("code"),
                    resultSet.getString("reference_name"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("closed")
                ))
            }
            return accounts
        }
    }
}