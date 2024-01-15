package sh.okx.bankoficenia.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.UserSession
import java.util.*

fun Route.templatedRoutes(userDao: SqlUserDao) {
    staticResources("/static", "static")
    authenticate("session-cookie", optional = true) {
        get("/") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/index.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        get("/accounts") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/accounts.html.peb", map))
        }
        get("/account/id") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/index.html.peb", map))
        }
        get("/account/id/transfer") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/transfer.html.peb", map))
        }
        get("/account/id/deposit") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/deposit.html.peb", map))
        }
        get("/account/id/withdraw") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/withdraw.html.peb", map))
        }
    }
}