package sh.okx.bankoficenia.backend.database

import javax.sql.DataSource

data class SqlAccountDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS accounts (" +
                        "\"id\" BIGSERIAL," +
                        "user_id BIGINT NOT NULL, " +
                        "code VARCHAR(16) UNIQUE NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "closed BOOL NOT NULL DEFAULT FALSE, " +
                        "registered TIMESTAMP DEFAULT NOW(), " +
                        "PRIMARY KEY (\"id\"))");
        }
    }
}