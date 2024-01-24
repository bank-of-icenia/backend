package sh.okx.bankoficenia.backend.plugin

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import sh.okx.bankoficenia.backend.model.UserSession

val KEY_CSRF = AttributeKey<String>("csrf")
val KEY_MAP = AttributeKey<MutableMap<String, Any>>("map")


val CsrfPlugin = createRouteScopedPlugin("CsrfPlugin") {
    on(AuthenticationChecked) { call ->
        val hashmap = call.attributes.computeIfAbsent(KEY_MAP) { HashMap() }
        val user = call.principal<UserSession>()
        if (user != null) {
            call.attributes.put(KEY_CSRF, user.csrf)
            hashmap["csrf"] = user.csrf
        }
    }
}