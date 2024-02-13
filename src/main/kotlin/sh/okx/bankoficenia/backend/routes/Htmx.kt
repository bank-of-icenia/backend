package sh.okx.bankoficenia.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.collections.HashMap
import kotlin.collections.mapOf
import kotlin.collections.set
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.plugin.AdminPlugin
import sh.okx.bankoficenia.backend.plugin.KEY_ADMIN_USER

fun Route.htmxRoutes(
    userDao: SqlUserDao,
    accountDao: SqlAccountDao,
    ledgerDao: SqlLedgerDao
) {
    authenticate("session-cookie") {
        install(AdminPlugin) { pluginUserDao = userDao }

        post("/admin/createuser") {
            val user = call.attributes[KEY_ADMIN_USER]
            val parameters = call.receiveParameters()
            val discordId = parameters["discord-id"]?.toLongOrNull()
            if (discordId == null || discordId < 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PebbleContent(
                        "snippets/admin/users/create-user.html.peb",
                        mapOf(
                            "state" to "default",
                            "error" to "Discord ID is missing!"
                        )
                    )
                )
                return@post
            }
            val ign = parameters["ign"]
            if (ign.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PebbleContent(
                        "snippets/admin/users/create-user.html.peb",
                        mapOf(
                            "state" to "default",
                            "error" to "IGN is missing!",
                            "discord_id" to discordId
                        )
                    )
                )
                return@post
            }
            val userIdOpt = userDao.createUser(discordId, ign)
            if (userIdOpt == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    PebbleContent(
                        "snippets/admin/users/create-user.html.peb",
                        mapOf(
                            "state" to "default",
                            "error" to "User already exists!",
                            "discord_id" to discordId,
                            "ign" to ign
                        )
                    )
                )
                return@post
            }
            val map = HashMap<String, Any>()
            map["state"] = "done"
            map["user"] = user
            map["userId"] = userIdOpt
            map["ign"] = ign
            call.respond(HttpStatusCode.OK, PebbleContent(
                "snippets/admin/users/create-user.html.peb",
                map
            ))
        }
    }
}
