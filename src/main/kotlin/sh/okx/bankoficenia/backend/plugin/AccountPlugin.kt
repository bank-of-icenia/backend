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

val KEY_USER = AttributeKey<User>("user")
val KEY_ACCOUNT = AttributeKey<Account>("account")


class Configuration {
    lateinit var pluginUserDao: SqlUserDao
    lateinit var pluginAccountDao: SqlAccountDao
}

// This plugin deduplicates some logic to make sure that there is a real account, it is opened, and the current user owns it
val AccountPlugin = createRouteScopedPlugin("AccountPlugin", { Configuration() }) {
    on(AuthenticationChecked) { call ->
        val user = call.principal<UserSession>()?.let { pluginConfig.pluginUserDao.read(it.userId) }
        val account = call.parameters["id"]?.toLongOrNull()?.let { pluginConfig.pluginAccountDao.read(it) }

        if (user == null || account == null || account.closed || account.userId != user.id) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }

        call.attributes.put(KEY_USER, user)
        call.attributes.put(KEY_ACCOUNT, account)
    }
}