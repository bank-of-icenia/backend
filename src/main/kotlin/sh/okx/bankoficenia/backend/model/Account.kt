package sh.okx.bankoficenia.backend.model

data class Account(
    val id: Long,
    val userId: Long?,
    val code: String?,
    val referenceName: String?,
    val name: String,
    val accountType: AccountType,
    val closed: Boolean,
    val inDirectory: Boolean,
)