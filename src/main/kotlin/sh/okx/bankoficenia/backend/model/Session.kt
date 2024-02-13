package sh.okx.bankoficenia.backend.model

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal

suspend fun getSessionApi(
    call: ApplicationCall
): UserSession? {
    return call.principal<UserSession>()
}

data class UserSession(val userId: Long, val csrf: String) : Principal
