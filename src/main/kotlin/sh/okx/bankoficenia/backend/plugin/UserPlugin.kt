package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.User

val KEY_READ_USER = AttributeKey<User>("read_user")

class UserConfiguration {
    lateinit var pluginUserDao: SqlUserDao
}

val UserPlugin = createRouteScopedPlugin("UserPlugin", { UserConfiguration() }) {
    on(AuthenticationChecked) { call ->
        val user = call.parameters["id"]?.toLongOrNull()?.let { pluginConfig.pluginUserDao.getUserById(it) }
        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }
        call.attributes.put(KEY_READ_USER, user)
    }
}
