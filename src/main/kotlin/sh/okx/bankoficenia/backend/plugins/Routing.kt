package sh.okx.bankoficenia.backend.plugins

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.*
import sh.okx.bankoficenia.backend.plugin.CsrfPlugin
import sh.okx.bankoficenia.backend.routes.discordLoginRoute
import sh.okx.bankoficenia.backend.routes.htmxRoutes
import sh.okx.bankoficenia.backend.routes.templatedRoutes
import sh.okx.bankoficenia.backend.routes.templated.templatedAdminRoutes
import sh.okx.bankoficenia.backend.routes.templated.templatedRoutes

fun Application.configureRouting(httpClient: HttpClient, sessionDao: SqlSessionDao, userDao: SqlUserDao, accountDao: SqlAccountDao, ledgerDao: SqlLedgerDao, unbankedDao: SqlUnbankedDao, webhook: String) {
    routing {
        install(CsrfPlugin)

        discordLoginRoute(httpClient, sessionDao, userDao)
        templatedRoutes(userDao, accountDao, ledgerDao, unbankedDao, httpClient, webhook)
        templatedAdminRoutes(userDao, accountDao, ledgerDao)
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