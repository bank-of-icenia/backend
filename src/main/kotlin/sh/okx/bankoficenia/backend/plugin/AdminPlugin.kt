package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
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