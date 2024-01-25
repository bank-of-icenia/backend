package sh.okx.bankoficenia.backend.model

data class AccountAndUser(
    val id: Long,
    val userId: Long?,
    val userIgn: String?,
    val userDiscord: String?,
    val code: String?,
    val referenceName: String?,
    val name: String,
    val closed: Boolean,
    val inDirectory: Boolean,
)