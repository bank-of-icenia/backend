package sh.okx.bankoficenia.backend.plugin

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.util.AttributeKey
import sh.okx.bankoficenia.backend.model.UserSession

const val CSRF_KEY = "csrf"
val KEY_CSRF = AttributeKey<String>(CSRF_KEY)
val KEY_MAP = AttributeKey<MutableMap<String, Any>>("map")

val CsrfPlugin = createRouteScopedPlugin("CsrfPlugin") {
    on(AuthenticationChecked) { call ->
        val hashmap = call.attributes.computeIfAbsent(KEY_MAP) {
            HashMap()
        }
        call.principal<UserSession>()?.let {
            call.attributes.put(KEY_CSRF, it.csrf)
            hashmap[CSRF_KEY] = it.csrf
        }
    }
}
