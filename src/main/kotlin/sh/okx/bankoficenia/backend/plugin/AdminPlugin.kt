package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.User
import sh.okx.bankoficenia.backend.model.UserSession

val KEY_ADMIN_USER = AttributeKey<User>("admin")

class AdminConfiguration {
    lateinit var pluginUserDao: SqlUserDao
}

val AdminPlugin = createRouteScopedPlugin("AdminPlugin", { AdminConfiguration() }) {
    on(AuthenticationChecked) { call ->
        val adminUser = call.principal<UserSession>()?.let { pluginConfig.pluginUserDao.getUserById(it.userId) }
        if (adminUser == null || !adminUser.admin) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }

        call.attributes.put(KEY_ADMIN_USER, adminUser)
    }
}
