package sh.okx.bankoficenia.backend.model

import java.time.Instant

data class Transaction(
    val id: Long,
    val account: Long,
    val referencedAccount: String,
    val type: TransactionType,
    val message: String,
    val amount: Double,
    val timestamp: Instant,
    val runningTotal: Double,
)

enum class TransactionType {
    DEBIT,
    CREDIT,
}