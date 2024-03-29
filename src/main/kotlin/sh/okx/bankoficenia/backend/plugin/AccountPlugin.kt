package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
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
    var optionalAccount: Boolean = false
}

// This plugin deduplicates some logic to make sure that there is a real account, it is opened, and the current user owns it
val AccountPlugin = createRouteScopedPlugin("AccountPlugin", { Configuration() }) {
    on(AuthenticationChecked) { call ->
        val user = call.principal<UserSession>()?.let { pluginConfig.pluginUserDao.getUserById(it.userId) }
        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }

        val account = call.parameters["id"]?.let { pluginConfig.pluginAccountDao.readByCode(it) }
        if (account != null && !account.closed && account.userId == user.id) {
            call.attributes.put(KEY_ACCOUNT, account)
        }
        else if (!pluginConfig.optionalAccount) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }

        call.attributes.put(KEY_USER, user)
    }
}
