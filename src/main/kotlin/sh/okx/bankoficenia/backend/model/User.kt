package sh.okx.bankoficenia.backend.model

import java.time.LocalDateTime

data class User(
    val id: Long,
    val discordId: Long,
    val discordUsername: String,
    val discordGlobalname: String,
    val registered: LocalDateTime
)