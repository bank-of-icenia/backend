package sh.okx.bankoficenia.backend.database

import javax.sql.DataSource

data class SqlUnbankedDao(
    val dataSource: DataSource
) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS unbanked (" +
                            "\"id\" BIGSERIAL," +
                            "tx_id BIGINT UNIQUE," +
                            "ign TEXT UNIQUE," +
                            "paid BOOLEAN DEFAULT false," +
                            "PRIMARY KEY (\"id\"))"
                )
        }
    }

    fun ledgeUnbanked(
        accountFrom: Long,
        accountTo: Long,
        amount: String,
        description: String,
        ign: String
    ): Boolean {
        dataSource.connection.use {
            try {
                it.autoCommit = false

                val txId = ledgeNoTransaction(it, accountFrom, accountTo, amount, description, false)
                if (txId == null) {
                    it.rollback()
                    return false
                }

                val stmt = it.prepareStatement("INSERT INTO unbanked (tx_id, ign) VALUES (?, ?)")
                stmt.setLong(1, txId)
                stmt.setString(2, ign)
                stmt.executeUpdate()

                it.commit()
                return true
            } finally {
                it.autoCommit = true
            }
        }
    }
}
