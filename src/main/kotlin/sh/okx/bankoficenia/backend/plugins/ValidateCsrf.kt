package sh.okx.bankoficenia.backend.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import sh.okx.bankoficenia.backend.plugin.KEY_CSRF

suspend fun validateCsrf(
    call: ApplicationCall,
    csrf: String?,
): Boolean {
    val userCsrf = call.attributes.getOrNull(KEY_CSRF)
    if (userCsrf == null || csrf == null || userCsrf != csrf) {
        call.respond(HttpStatusCode.Forbidden)
        return false
    }
    return true
}
