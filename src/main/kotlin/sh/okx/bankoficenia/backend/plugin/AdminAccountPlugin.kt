package sh.okx.bankoficenia.backend.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.model.Account

val KEY_ADMIN_ACCOUNT = AttributeKey<Account>("admin_account")

class AdminAccountConfiguration {
    lateinit var pluginAccountDao: SqlAccountDao
}

val AdminAccountPlugin = createRouteScopedPlugin("AdminAccountPlugin", { AdminAccountConfiguration() }) {
    on(AuthenticationChecked) { call ->
        val account = call.parameters["id"]?.toLongOrNull()?.let { pluginConfig.pluginAccountDao.read(it) }

        if (account == null) {
            call.respond(HttpStatusCode.NotFound)
            return@on
        }

        call.attributes.put(KEY_ADMIN_ACCOUNT, account)
    }
}
