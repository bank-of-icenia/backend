package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.Account
import sh.okx.bankoficenia.backend.model.AccountAndUser
import sh.okx.bankoficenia.backend.model.AccountType
import sh.okx.bankoficenia.backend.model.DirectoryAccount
import java.security.SecureRandom
import javax.sql.DataSource

data class SqlAccountDao(val dataSource: DataSource) {
    private var random = SecureRandom()

    init {
        dataSource.connection.use {
            it.createStatement()
                .execute("DO $$ BEGIN CREATE TYPE ACCOUNT_TYPE AS ENUM ('DIVIDEND', 'EXPENSE', 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE'); EXCEPTION WHEN duplicate_object THEN null; END $$")
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
                            "account_type ACCOUNT_TYPE NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "in_directory BOOL NOT NULL DEFAULT FALSE, " +
                            "PRIMARY KEY (\"id\"), " +
                            "CHECK (reference_name IS NOT NULL OR (user_id IS NOT NULL AND code IS NOT NULL)))"
                );
            it.createStatement()
                .execute("INSERT INTO accounts (\"id\", reference_name, name, account_type) VALUES (1, 'Bank of Icenia', 'Admin Account', 'ASSET') ON CONFLICT DO NOTHING")
        }
    }

    fun createAccount(userId: Long, name: String): Long? {
        dataSource.connection.use {
            for (i in 1..100) {
                val stmt =
                    it.prepareStatement("INSERT INTO accounts (user_id, code, name, account_type) VALUES (?, ?, ?, 'LIABILITY') ON CONFLICT DO NOTHING RETURNING \"id\"")
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
                    AccountType.valueOf(resultSet.getString("account_type")),
                    resultSet.getBoolean("closed"),
                    resultSet.getBoolean("in_directory"),
                ))
            }
            return accounts
        }
    }

    fun setInDirectory(accountId: Long, inDirectory: Boolean): Boolean {
        dataSource.connection.use {
            val stmt = it.prepareStatement("UPDATE accounts SET in_directory = ? WHERE \"id\" = ? AND NOT closed")
            stmt.setBoolean(1, inDirectory)
            stmt.setLong(2, accountId)

            return stmt.executeUpdate() > 0
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
                    AccountType.valueOf(resultSet.getString("account_type")),
                    resultSet.getBoolean("closed"),
                    resultSet.getBoolean("in_directory"),
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
                    AccountType.valueOf(resultSet.getString("account_type")),
                    resultSet.getBoolean("closed"),
                    resultSet.getBoolean("in_directory"),
                )
            }
            return null
        }
    }

    fun getAllAccountsAndUser(): List<AccountAndUser> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM accounts LEFT JOIN users ON accounts.user_id = users.id ORDER BY accounts.id")

            val resultSet = stmt.executeQuery()
            val accounts = ArrayList<AccountAndUser>()
            while (resultSet.next()) {
                val accUserId = resultSet.getLong("user_id")
                val accUserIdNull = resultSet.wasNull()
                accounts.add(AccountAndUser(
                    resultSet.getLong("id"),
                    if (accUserIdNull) null else accUserId,
                    resultSet.getString("ign"),
                    resultSet.getString("discord_globalname"),
                    resultSet.getString("code"),
                    resultSet.getString("reference_name"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("closed"),
                    resultSet.getBoolean("in_directory"),
                ))
            }
            return accounts
        }
    }

    fun getDirectoryAccounts(): List<DirectoryAccount> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT ign, code FROM accounts INNER JOIN users ON accounts.user_id = users.id AND users.ign IS NOT NULL WHERE in_directory ORDER BY users.ign")

            val resultSet = stmt.executeQuery()
            val accounts = ArrayList<DirectoryAccount>()
            while (resultSet.next()) {
                accounts.add(
                    DirectoryAccount(
                    resultSet.getString("ign"),
                    resultSet.getString("code"),
                ))
            }
            return accounts
        }
    }
}