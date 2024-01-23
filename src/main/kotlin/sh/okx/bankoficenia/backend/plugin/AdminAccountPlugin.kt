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