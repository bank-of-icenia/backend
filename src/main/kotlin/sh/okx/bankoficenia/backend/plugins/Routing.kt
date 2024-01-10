package sh.okx.bankoficenia.backend.plugins

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.routes.discordLoginRoute
import sh.okx.bankoficenia.backend.routes.templatedRoutes
import sh.okx.bankoficenia.backend.routes.userInfoRoute

fun Application.configureRouting(httpClient: HttpClient, sessionDao: SqlSessionDao, userDao: SqlUserDao) {
    routing {
        userInfoRoute(httpClient)
        discordLoginRoute(httpClient, sessionDao, userDao)
        templatedRoutes(userDao)
    }
}