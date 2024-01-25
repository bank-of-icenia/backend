package sh.okx.bankoficenia.backend.plugins

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.routes.discordLoginRoute
import sh.okx.bankoficenia.backend.routes.templatedRoutes
import sh.okx.bankoficenia.backend.routes.userInfoRoute

fun Application.configureRouting(httpClient: HttpClient, sessionDao: SqlSessionDao, userDao: SqlUserDao, accountDao: SqlAccountDao, ledgerDao: SqlLedgerDao, webhook: String) {
    routing {
        userInfoRoute(httpClient)
        discordLoginRoute(httpClient, sessionDao, userDao)
        templatedRoutes(userDao, accountDao, ledgerDao, httpClient, webhook)
        resources()
    }
}

fun Route.resources() {
    route("/") {
        install(DefaultHeaders) {
            header("Cache-Control", "max-age=86400") // will send this header with each response
        }
        resource("/favicon.ico", "static/assets/bankoficenia.png")
        staticResources("/static", "static")
    }
}