package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.AccountType
import sh.okx.bankoficenia.backend.model.Transaction
import sh.okx.bankoficenia.backend.model.TransactionType
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

data class SqlLedgerDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            // sorry, postgres doesn't have CREATE TYPE IF NOT EXISTS!
            it.createStatement()
                .execute("DO $$ BEGIN CREATE TYPE TRANSACTION_TYPE AS ENUM ('DEBIT', 'CREDIT'); EXCEPTION WHEN duplicate_object THEN null; END $$")
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS ledger (" +
                            "\"id\" BIGSERIAL," +
                            "account BIGINT NOT NULL, " +
                            "referenced_account BIGINT NOT NULL, " +
                            "\"type\" TRANSACTION_TYPE NOT NULL, " +
                            "message TEXT NOT NULL, " +
                            "amount NUMERIC(10, 4), " +
                            "\"timestamp\" TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"))"
                )
        }
    }

    fun reconcileTransactionType(): String {
        dataSource.connection.use {
            val resultSet = it.createStatement()
                .executeQuery("SELECT SUM(CASE WHEN \"type\" = 'DEBIT' THEN amount ELSE -amount END) AS amount FROM ledger")

            if (!resultSet.next()) {
                return "0.0000";
            }

            return resultSet.getString("amount") ?: "0.0000"
        }
    }
    fun reconcileAccountType(): Pair<String, String> {
        dataSource.connection.use {
            val resultSet = it.createStatement()
                .executeQuery("SELECT " +
                        "account_type IN ('DIVIDEND', 'EXPENSE', 'ASSET') AS debit, " +
                        "SUM(CASE WHEN (\"type\" != 'DEBIT') != (account_type IN ('DIVIDEND', 'EXPENSE', 'ASSET')) THEN amount ELSE -amount END) AS amount " +
                        "FROM ledger " +
                        "INNER JOIN accounts ON accounts.id = ledger.account " +
                        "GROUP BY account_type IN ('DIVIDEND', 'EXPENSE', 'ASSET') " +
                        "ORDER BY debit")

            if (!resultSet.next()) {
                return "0.0000" to "0.0000";
            }

            val amount = resultSet.getString("amount") ?: "0.0000"

            if (!resultSet.next()) {
                return amount to "0.000"
            }

            val amount2 = resultSet.getString("amount") ?: "0.0000"
            return amount to amount2
        }
    }



    /**
     * Returned amounts are double and therefore imprecise and not suitable for numerical computation, only display.
     */
    fun getBalances(accounts: List<Long>): List<Double> {
        if (accounts.isEmpty()) {
            return Collections.emptyList()
        }
        dataSource.connection.use {
            val arr = buildString {
                for (i in accounts.indices) {
                    if (i > 0) {
                        append(",")
                    }
                    append("?")
                }
            }
            val stmt = it.prepareStatement(
                "SELECT account, SUM(CASE WHEN \"type\" = 'DEBIT' THEN amount ELSE -amount END) AS amount FROM ledger WHERE \"account\" IN ($arr) GROUP BY account"
            )
            for (i in accounts.indices) {
                stmt.setLong(1 + i, accounts[i])
            }
            val balances = HashMap<Long, Double>()
            val resultSet = stmt.executeQuery()
            while (resultSet.next()) {
                balances[resultSet.getLong("account")] = resultSet.getDouble("amount")
            }

            val balancesArray = ArrayList<Double>(accounts.size)
            for (account in accounts) {
                val am = balances.getOrDefault(account, 0.0)
                balancesArray.add(if (am == 0.0) 0.0 else am)
            }
            return balancesArray
        }
    }

    fun getTransactions(account: Long): List<Transaction> {
        dataSource.connection.use {
            val stmt = it.prepareStatement(
                "SELECT ledger.id, account, type, message, amount, timestamp, a2.account_type as account_type, COALESCE(accounts.reference_name, CASE WHEN users.ign IS NOT NULL THEN accounts.code || ' (' || users.ign || ')' ELSE accounts.code END) AS referenced_account_code, " +
                        "SUM(CASE WHEN \"type\" = 'DEBIT' THEN amount ELSE -amount END) OVER (PARTITION BY \"account\" ORDER BY \"timestamp\", type DESC, ledger.id) AS running_total " +
                        "FROM ledger " +
                        "INNER JOIN accounts ON accounts.id = referenced_account " +
                        "INNER JOIN accounts a2 ON a2.id = account " +
                        "LEFT JOIN users ON accounts.user_id = users.id " +
                        "WHERE \"account\" = ? ORDER BY \"timestamp\" DESC, type, ledger.id DESC"
            )
            stmt.setLong(1, account)

            val resultSet = stmt.executeQuery()
            val transactions = ArrayList<Transaction>()
            while (resultSet.next()) {
                var runningTotal = resultSet.getDouble("running_total")
                if (!AccountType.valueOf(resultSet.getString("account_type")).isNormalDebit() && runningTotal != 0.0) {
                    runningTotal = -runningTotal
                }
                transactions.add(
                    Transaction(
                        resultSet.getLong("id"),
                        resultSet.getLong("account"),
                        resultSet.getString("referenced_account_code"),
                        TransactionType.valueOf(resultSet.getString("type")),
                        resultSet.getString("message"),
                        resultSet.getDouble("amount"),
                        resultSet.getTimestamp("timestamp").toInstant(),
                        runningTotal
                    )
                )
            }
            return transactions
        }
    }

    fun ledge(accountFrom: Long, accountTo: Long, amount: String, description: String, force: Boolean = false): Boolean {
        dataSource.connection.use {
            try {
                it.autoCommit = false

                if (ledgeNoTransaction(it, accountFrom, accountTo, amount, description, force) != null) {
                    it.commit()
                    return true
                } else {
                    it.rollback()
                    return false
                }
            } finally {
                it.autoCommit = true
            }
        }
    }
}

fun ledgeNoTransaction(it: Connection, accountFrom: Long, accountTo: Long, amount: String, description: String, force: Boolean): Long? {
    // Shit but easy
    it.createStatement().execute("LOCK TABLE ledger")

    if (!force) {
        val stmt = it.prepareStatement(
            "SELECT SUM(CASE WHEN \"type\" = 'DEBIT' THEN -amount ELSE amount END) AS amount FROM ledger WHERE \"account\" = ?"
        )
        stmt.setLong(1, accountFrom)
        var balance = "0"
        val resultSet = stmt.executeQuery()
        if (resultSet.next()) {
            balance = resultSet.getString("amount") ?: "0"
        }

        val balStmt = it.prepareStatement(
            "SELECT CAST(? AS NUMERIC(10, 4)) >= CAST(? AS NUMERIC(10, 4)) AS has_balance"
        )
        balStmt.setString(1, balance)
        balStmt.setString(2, amount)

        val balResultSet = balStmt.executeQuery()
        if (!balResultSet.next()) {
            return null
        }
        val hasBalance = balResultSet.getBoolean("has_balance")
        if (!hasBalance) {
            return null
        }
    }

    val credit =
        it.prepareStatement("INSERT INTO ledger (account, referenced_account, \"type\", message, amount) VALUES (?, ?, 'CREDIT', ?, CAST(? AS NUMERIC(10, 4)))")
    credit.setLong(1, accountTo)
    credit.setLong(2, accountFrom)
    credit.setString(3, description)
    credit.setString(4, amount)
    credit.executeUpdate()

    val debit =
        it.prepareStatement("INSERT INTO ledger (account, referenced_account, \"type\", message, amount) VALUES (?, ?, 'DEBIT', ?, CAST(? AS NUMERIC(10, 4))) RETURNING id")
    debit.setLong(1, accountFrom)
    debit.setLong(2, accountTo)
    debit.setString(3, description)
    debit.setString(4, amount)
    val resultSet = debit.executeQuery()
    if (!resultSet.next()) {
        return null
    }

    return resultSet.getLong("id")
}