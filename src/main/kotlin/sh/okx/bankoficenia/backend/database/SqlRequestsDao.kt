package sh.okx.bankoficenia.backend.database

import javax.sql.DataSource

data class SqlRequestsDao(val dataSource: DataSource) {
    init {
        dataSource.connection.use {
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS teller_deposit_requests (" +
                            "\"id\" BIGSERIAL," +
                            "user_id BIGINT NOT NULL, " +
                            "code VARCHAR(16) UNIQUE NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"))"
                );
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS teller_withdraw_requests (" +
                            "\"id\" BIGSERIAL," +
                            "user_id BIGINT NOT NULL, " +
                            "code VARCHAR(16) UNIQUE NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"))"
                )
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS monument_bank_deposit_requests (" +
                            "\"id\" BIGSERIAL," +
                            "user_id BIGINT NOT NULL, " +
                            "code VARCHAR(16) UNIQUE NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"))"
                );
            it.createStatement()
                .execute(
                    "CREATE TABLE IF NOT EXISTS monument_bank_withdraw_requests (" +
                            "\"id\" BIGSERIAL," +
                            "user_id BIGINT NOT NULL, " +
                            "code VARCHAR(16) UNIQUE NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "closed BOOL NOT NULL DEFAULT FALSE, " +
                            "registered TIMESTAMP DEFAULT NOW(), " +
                            "PRIMARY KEY (\"id\"))"
                );
        }
    }
}