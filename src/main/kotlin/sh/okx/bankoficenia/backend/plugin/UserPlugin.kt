package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.Account
import sh.okx.bankoficenia.backend.model.User
import sh.okx.bankoficenia.backend.model.UserSession

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