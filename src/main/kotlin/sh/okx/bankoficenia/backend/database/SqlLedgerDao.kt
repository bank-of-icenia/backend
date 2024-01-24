package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.Transaction
import sh.okx.bankoficenia.backend.model.TransactionType
import java.util.*
import javax.sql.DataSource
import kotlin.collections.ArrayList

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
                balancesArray.add(balances.getOrDefault(account, 0.0))
            }
            return balancesArray
        }
    }

    fun getTransactions(account: Long): List<Transaction> {
        dataSource.connection.use {
            val stmt = it.prepareStatement("SELECT *, COALESCE(accounts.reference_name, CASE WHEN users.ign IS NOT NULL THEN accounts.code || ' (' || users.ign || ')' ELSE accounts.code END) AS referenced_account_code, " +
                    "SUM(CASE WHEN \"type\" = 'DEBIT' THEN amount ELSE -amount END) OVER (PARTITION BY \"account\" ORDER BY \"timestamp\", ledger.id) AS running_total " +
                    "FROM ledger " +
                    "INNER JOIN accounts ON accounts.id = referenced_account " +
                    "LEFT JOIN users ON accounts.user_id = users.id " +
                    "WHERE \"account\" = ? ORDER BY \"timestamp\" DESC")
            stmt.setLong(1, account)

            val resultSet = stmt.executeQuery()
            val transactions = ArrayList<Transaction>()
            while (resultSet.next()) {
                transactions.add(
                    Transaction(
                        resultSet.getLong("id"),
                        resultSet.getLong("account"),
                        resultSet.getString("referenced_account_code"),
                        TransactionType.valueOf(resultSet.getString("type")),
                        resultSet.getString("message"),
                        resultSet.getDouble("amount"),
                        resultSet.getTimestamp("timestamp").toInstant(),
                        resultSet.getDouble("running_total")
                    )
                )
            }
            return transactions
        }
    }

    fun ledge(accountFrom: Long, accountTo: Long, amount: String, description: String): Boolean {
        dataSource.connection.use {
            try {
                it.autoCommit = false

                // Shit but easy
                it.createStatement().execute("LOCK TABLE ledger")

                val stmt = it.prepareStatement(
                    "SELECT SUM(CASE WHEN \"type\" = 'DEBIT' THEN amount ELSE -amount END) AS amount FROM ledger WHERE \"account\" = ?"
                )
                stmt.setLong(1, accountFrom)
                var balance = "0"
                val resultSet = stmt.executeQuery()
                if (resultSet.next()) {
                    balance = resultSet.getString("amount")
                }

                val balStmt = it.prepareStatement(
                    "SELECT CAST(? AS NUMERIC(10, 4)) >= CAST(? AS NUMERIC(10, 4)) AS has_balance"
                )
                balStmt.setString(1, balance)
                balStmt.setString(2, amount)

                val balResultSet = balStmt.executeQuery()
                if (!balResultSet.next()) {
                    return false
                }
                val hasBalance = balResultSet.getBoolean("has_balance")
                if (!hasBalance) {
                    return false
                }

                val credit = it.prepareStatement("INSERT INTO ledger (account, referenced_account, \"type\", message, amount) VALUES (?, ?, 'CREDIT', ?, CAST(? AS NUMERIC(10, 4)))")
                credit.setLong(1, accountFrom)
                credit.setLong(2, accountTo)
                credit.setString(3, description)
                credit.setString(4, amount)
                credit.executeUpdate()

                val debit = it.prepareStatement("INSERT INTO ledger (account, referenced_account, \"type\", message, amount) VALUES (?, ?, 'DEBIT', ?, CAST(? AS NUMERIC(10, 4)))")
                debit.setLong(1, accountTo)
                debit.setLong(2, accountFrom)
                debit.setString(3, description)
                debit.setString(4, amount)
                debit.executeUpdate()
                return true
            } finally {
                it.autoCommit = true
            }
        }
    }
}