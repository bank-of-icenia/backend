package sh.okx.bankoficenia.backend.model

import java.time.LocalDateTime

data class User(
    val id: Long,
    val ign: String?,
    val discordId: Long,
    val discordUsername: String?,
    val discordGlobalname: String?,
    val admin: Boolean,
    val registered: LocalDateTime
)