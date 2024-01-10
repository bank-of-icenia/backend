package sh.okx.bankoficenia.backend.model

import io.ktor.server.application.*
import io.ktor.server.auth.*

suspend fun getSessionApi(
    call: ApplicationCall
): UserSession? {
    val userSession: UserSession? = call.principal<UserSession>()
    if (userSession == null) {
        return null
    }
    return userSession
}

data class UserSession(val userId: Long) : Principal
