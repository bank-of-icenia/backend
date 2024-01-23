package sh.okx.bankoficenia.backend.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.UserSession
import sh.okx.bankoficenia.backend.plugin.*
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.*
import java.util.regex.Pattern

val amountFormat = DecimalFormat("0.0000")
val amountRegex: Pattern = Pattern.compile("\\d{0,10}(\\.\\d{1,4})?")
val descriptionRegex: Pattern = Pattern.compile("[,.!\"'$()?\\-_=+&*^%;:/0-9A-z]{0,32}")
val codeRegex: Pattern = Pattern.compile("\\d\\d-\\d\\d-\\d\\d")

fun Route.templatedRoutes(userDao: SqlUserDao, accountDao: SqlAccountDao, ledgerDao: SqlLedgerDao) {
    staticResources("/static", "static")
    authenticate("session-cookie", optional = true) {
        get("/") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }

            val map = HashMap<String, Any>()
            if (user != null) {
                map["user"] = user
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/index.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        get("/accounts") {
            val user = call.principal<UserSession>()?.let { userDao.read(it.userId) }
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val map = HashMap<String, Any>()
            val accounts = accountDao.getAccounts(user.id)
            map["accounts"] = accounts
            map["balances"] = ledgerDao.getBalances(accounts.map { it.id }).map { String.format("%.4f", it) }
            map["user"] = user
            call.respond(HttpStatusCode.OK, PebbleContent("pages/accounts.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        install(AccountPlugin) {
            pluginUserDao = userDao
            pluginAccountDao = accountDao
        }
        get("/account/{id}") {
            val transactions = ledgerDao.getTransactions(call.attributes[KEY_ACCOUNT].id)

            val map = HashMap<String, Any>()

            map["user"] = call.attributes[KEY_USER]
            map["account"] = call.attributes[KEY_ACCOUNT]
            map["transactions"] = transactions
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/index.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        install(AccountPlugin) {
            pluginUserDao = userDao
            pluginAccountDao = accountDao
            optionalAccount = true
        }
        get("/transfer") {
            val map = HashMap<String, Any>()
            map["user"] = call.attributes[KEY_USER]
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            map["accounts"] = accountDao.getAccounts(call.attributes[KEY_USER].id)
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/transfer.html.peb", map))
        }
        get("/deposit") {
            val map = HashMap<String, Any>()
            map["user"] = call.attributes[KEY_USER]
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/deposit.html.peb", map))
        }
        get("/withdraw") {
            val map = HashMap<String, Any>()
            map["user"] = call.attributes[KEY_USER]
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/withdraw.html.peb", map))
        }

        post("/transfer/confirm") {
            val parameters = call.receiveParameters()
            val fromId = parameters["from"]?.toLongOrNull()
            val toCode = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = HashMap<String, Any>()
            map["user"] = call.attributes[KEY_USER]

            if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                || description == null || !descriptionRegex.matcher(description).matches()
                || toCode == null || !codeRegex.matcher(toCode).matches()
                || fromId == null
                || amountDec == null
                || amountDec == BigDecimal.ZERO
            ) {
                map["error"] = "generic"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            val account = accountDao.read(fromId)
            if (account?.code == null || account.closed || account.userId != call.attributes[KEY_USER].id) {
                map["error"] = "account_does_not_exist"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            if (accountDao.readByCode(toCode) == null) {
                map["error"] = "account_does_not_exist"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            val balance = ledgerDao.getBalances(listOf(account.id))[0]
            // Not a safe comparison but this isn't the real one
            if (amountDec > BigDecimal.valueOf(balance)) {
                map["error"] = "funds"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            map["from"] = account.code
            map["to"] = toCode
            map["amount"] = amountFormat.format(amountDec)
            map["description"] = description
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/transfer-confirm.html.peb", map))
        }

        post("/transfer/submit") {
            // The user should not have changed any of these parameters so we don't need to bother with a good page
            val parameters = call.receiveParameters()
            val fromId = parameters["from"]
            val toCode = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = HashMap<String, Any>()
            map["user"] = call.attributes[KEY_USER]

            if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                || description == null || !descriptionRegex.matcher(description).matches()
                || toCode == null || !codeRegex.matcher(toCode).matches()
                || fromId == null || !codeRegex.matcher(fromId).matches()
                || amountDec == null
                || amountDec == BigDecimal.ZERO
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val account = accountDao.readByCode(fromId)
            if (account == null || account.closed || account.userId != call.attributes[KEY_USER].id) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val accountTo = accountDao.readByCode(toCode)
            if (accountTo == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (!ledgerDao.ledge(account.id, accountTo.id, amountStr, description)) {
                map["error"] = "funds"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/transfer-submit.html.peb", map))
                return@post
            }

            map["from"] = account
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/transfer-submit.html.peb", map))
        }
    }

    authenticate("session-cookie") {
        install(AdminPlugin) { pluginUserDao = userDao }
        get("/admin") {
            val user = call.attributes[KEY_ADMIN_USER]

            val map = HashMap<String, Any>()
            map["user"] = user
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/index.html.peb", map))
        }
        get("/admin/createaccount") {
            val user = call.attributes[KEY_ADMIN_USER]

            val map = HashMap<String, Any>()
            map["user"] = user
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/createaccount.html.peb", map))
        }
        post("/admin/createaccount") {
            val user = call.attributes[KEY_ADMIN_USER]

            val parameters = call.receiveParameters()
            val userId: Long
            val createUser = "on" == parameters["create-user"]
            if (createUser) {
                val discordId = parameters["discord-id"]?.toLongOrNull()
                if (discordId == null || discordId < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PebbleContent(
                            "pages/admin/post_createaccount.html.peb",
                            mapOf("message" to "parameter_missing", "parameter" to "discord-id")
                        )
                    )
                    return@post
                }
                val ign = parameters["ign"]
                if (ign.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PebbleContent(
                            "pages/admin/post_createaccount.html.peb",
                            mapOf("message" to "parameter_missing", "parameter" to "ign")
                        )
                    )
                    return@post
                }
                val userIdOpt = userDao.createUser(discordId, ign)
                if (userIdOpt == null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        PebbleContent(
                            "pages/admin/post_createaccount.html.peb",
                            mapOf("message" to "user_exists")
                        )
                    )
                    return@post
                }
                userId = userIdOpt
            } else {
                val userIdParam = parameters["user-id"]?.toLongOrNull()
                if (userIdParam == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PebbleContent(
                            "pages/admin/post_createaccount.html.peb",
                            mapOf("message" to "parameter_missing", "parameter" to "user-id")
                        )
                    )
                    return@post
                }
                userId = userIdParam
            }

            val accountId = accountDao.createAccount(userId, "Holding Account")
            if (accountId == null) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    PebbleContent(
                        "pages/admin/post_createaccount.html.peb",
                        mapOf("message" to "error_duplicate")
                    )
                )
                return@post
            }

            val map = HashMap<String, Any>()
            map["user"] = user
            if (createUser) {
                map["message"] = "account_user_created"
                map["userId"] = userId
            } else {
                map["message"] = "account_created"
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/post_createaccount.html.peb", map))
        }
        get("/admin/users") {
            val adminUser = call.attributes[KEY_ADMIN_USER]

            val map = HashMap<String, Any>()
            map["user"] = adminUser
            map["users"] = userDao.getUsers()
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/users.html.peb", map))
        }
        get("/admin/accounts") {
            val adminUser = call.attributes[KEY_ADMIN_USER]

            val map = HashMap<String, Any>()
            map["user"] = adminUser
            map["accounts"] = accountDao.getAllAccounts()
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/accounts.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        install(AdminPlugin) { pluginUserDao = userDao }
        install(UserPlugin) { pluginUserDao = userDao }
        get("/admin/user/{id}") {
            val user = call.attributes[KEY_READ_USER]
            val adminUser = call.attributes[KEY_ADMIN_USER]
            val accounts = accountDao.getAccounts(user.id)

            val map = HashMap<String, Any>()
            map["user"] = adminUser
            map["read_user"] = user
            map["accounts"] = accounts
            map["balances"] = ledgerDao.getBalances(accounts.map { it.id })
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/user.html.peb", map))
        }
        get("/admin/user/{id}/editign") {
            val user = call.attributes[KEY_READ_USER]
            call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign_form.html.peb", mapOf("read_user" to user)))
        }
        put("/admin/user/{id}/editign") {
            val readUser = call.attributes[KEY_READ_USER]

            val parameters = call.receiveParameters()
            val ign = parameters["ign"]
            if (ign.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val user = userDao.updateIgn(readUser.id, ign)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@put
            }

            call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign.html.peb", mapOf("read_user" to user)))
        }

        get("/admin/user/{id}/readeditign") {
            val user = call.attributes[KEY_READ_USER]
            call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign.html.peb", mapOf("read_user" to user)))
        }
    }
    authenticate("session-cookie") {
        install(AdminPlugin) { pluginUserDao = userDao }
        install(AdminAccountPlugin) { pluginAccountDao = accountDao }

        get("/admin/account/{id}") {
            val account = call.attributes[KEY_ADMIN_ACCOUNT]
            val transactions = ledgerDao.getTransactions(account.id)
            call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/account.html.peb",
                mapOf("account" to account, "transactions" to transactions)))
        }
    }
}
