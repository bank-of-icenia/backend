package sh.okx.bankoficenia.backend.plugins

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.routes.discordLoginRoute
import sh.okx.bankoficenia.backend.routes.htmxRoutes
import sh.okx.bankoficenia.backend.routes.templatedRoutes

fun Application.configureRouting(httpClient: HttpClient, sessionDao: SqlSessionDao, userDao: SqlUserDao, accountDao: SqlAccountDao, ledgerDao: SqlLedgerDao, webhook: String, admin: Long) {
    routing {
        discordLoginRoute(httpClient, sessionDao, userDao)
        templatedRoutes(userDao, accountDao, ledgerDao, httpClient, webhook, admin)
        resources()
        htmxRoutes(userDao, accountDao, ledgerDao)
    }
}

fun Route.resources() {
    route("/") {
        install(DefaultHeaders) {
            header("Cache-Control", "max-age=86400") // will send this header with each response
        }
        get("/favicon.ico") {
            val favicon = call.resolveResource("static/assets/bankoficenia.png")
            if (favicon != null) {
                call.respond(favicon)
            }
        }
        staticResources("/static", "static")
    }
}