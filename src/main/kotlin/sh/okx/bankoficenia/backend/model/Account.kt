package sh.okx.bankoficenia.backend.model

data class Account(
    val id: Long,
    val userId: Long,
    val code: String,
    val name: String,
    val closed: Boolean
)