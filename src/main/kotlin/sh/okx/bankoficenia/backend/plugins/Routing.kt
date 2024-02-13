package sh.okx.bankoficenia.backend.plugins

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.resolveResource
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUnbankedDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.plugin.CsrfPlugin
import sh.okx.bankoficenia.backend.routes.discordLoginRoute
import sh.okx.bankoficenia.backend.routes.htmxRoutes
import sh.okx.bankoficenia.backend.routes.templated.templatedAdminRoutes
import sh.okx.bankoficenia.backend.routes.templated.templatedRoutes

fun Application.configureRouting(
    httpClient: HttpClient,
    sessionDao: SqlSessionDao,
    userDao: SqlUserDao,
    accountDao: SqlAccountDao,
    ledgerDao: SqlLedgerDao,
    unbankedDao: SqlUnbankedDao,
    webhook: String
) {
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
            call.resolveResource("static/assets/bankoficenia.png")?.let {
                call.respond(it)
            }
        }
        staticResources("/static", "static")
    }
}
