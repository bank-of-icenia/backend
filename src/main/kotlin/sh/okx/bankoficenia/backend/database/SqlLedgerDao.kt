package sh.okx.bankoficenia.backend.database

import java.util.*
import javax.sql.DataSource
import kotlin.collections.ArrayList

data class SqlLedgerDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            // sorry, postgres doesn't have CREATE TYPE IF NOT EXISTS!
            it.createStatement().execute("DO $$ BEGIN CREATE TYPE TRANSACTION_TYPE AS ENUM ('DEBIT', 'CREDIT'); EXCEPTION WHEN duplicate_object THEN null; END $$")
            it.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS ledger (" +
                        "\"id\" BIGSERIAL," +
                        "account BIGINT NOT NULL, " +
                        "referenced_account BIGINT NOT NULL, " +
                        "type TRANSACTION_TYPE NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "amount NUMERIC(10, 4), " +
                        "\"timestamp\" TIMESTAMP DEFAULT NOW(), " +
                        "PRIMARY KEY (\"id\"))")
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
                    append(i.toString())
                }
            }
            val stmt = it.prepareStatement(
                "SELECT account, SUM(amount) AS account_debit FROM ledger WHERE \"id\" IN ($arr) AND type = 'DEBIT' GROUP BY account UNION ALL " +
                        "SELECT account, SUM(amount) AS amount_credit FROM ledger where \"id\" IN ($arr) AND type = 'CREDIT' GROUP BY account"
            )
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
}