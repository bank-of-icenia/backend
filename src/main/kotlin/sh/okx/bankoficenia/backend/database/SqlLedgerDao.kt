package sh.okx.bankoficenia.backend.database

import javax.sql.DataSource

data class SqlLedgerDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS ledger (" +
                        "\"id\" BIGSERIAL," +
                        "account_id_from BIGINT NOT NULL, " +
                        "account_id_to BIGINT NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "amount NUMERIC(10, 4), " +
                        "\"timestamp\" TIMESTAMP DEFAULT NOW(), " +
                        "PRIMARY KEY (\"id\")," +
                        "CHECK (amount_in IS NOT NULL OR amount_out IS NOT NULL))");
        }
    }
}