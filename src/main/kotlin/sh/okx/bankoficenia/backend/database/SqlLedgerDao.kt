package sh.okx.bankoficenia.backend.database

import sh.okx.bankoficenia.backend.model.Transaction
import sh.okx.bankoficenia.backend.model.TransactionType
import java.sql.Timestamp
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
                "SELECT account, SUM(amount) AS account_debit FROM ledger WHERE \"id\" IN ($arr) AND \"type\" = 'DEBIT' GROUP BY account UNION ALL " +
                        "SELECT account, SUM(amount) AS amount_credit FROM ledger where \"id\" IN ($arr) AND \"type\" = 'CREDIT' GROUP BY account"
            )
            for (i in accounts.indices) {
                stmt.setLong(1 + i, accounts[i])
                stmt.setLong(1 + i + accounts.size, accounts[i])
            }
            val balances = HashMap<Long, Double>()
            val resultSet = stmt.executeQuery()
            while (resultSet.next()) {
                balances.merge(resultSet.getLong("account"), resultSet.getDouble("amount_debit"), Double::plus)
                balances.merge(resultSet.getLong("account"), -resultSet.getDouble("amount_credit"), Double::plus)
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
            val stmt = it.prepareStatement("SELECT * FROM ledger WHERE \"id\" = ? ORDER BY \"timestamp\" DESC")
            stmt.setLong(1, account)

            val resultSet = stmt.executeQuery()
            val transactions = ArrayList<Transaction>()
            while (resultSet.next()) {
                transactions.add(
                    Transaction(
                        resultSet.getLong("id"),
                        resultSet.getLong("account"),
                        resultSet.getLong("referenced_account"),
                        TransactionType.valueOf(resultSet.getString("type")),
                        resultSet.getString("message"),
                        resultSet.getDouble("amount"),
                        resultSet.getTimestamp("timestamp").toInstant()
                    )
                )
            }
            return transactions
        }
    }
}