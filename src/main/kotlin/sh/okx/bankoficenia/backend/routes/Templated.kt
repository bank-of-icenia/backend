package sh.okx.bankoficenia.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.UserSession
import java.util.*

fun Route.templatedRoutes(userDao: SqlUserDao) {
    authenticate("session-cookie", optional = true) {
        get("/") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            // Todo if logged on, redirect to accounts list
            call.respond(HttpStatusCode.OK, PebbleContent("pages/index.html", map))
        }
    }
}