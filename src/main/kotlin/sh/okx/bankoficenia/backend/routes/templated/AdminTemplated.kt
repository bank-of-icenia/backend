package sh.okx.bankoficenia.backend.routes.templated

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.plugin.*
import sh.okx.bankoficenia.backend.plugins.validateCsrf
import java.math.BigDecimal
import sh.okx.bankoficenia.backend.database.IGN_COLUMN
import sh.okx.bankoficenia.backend.database.UpdateTargets

fun Route.templatedAdminRoutes(
    userDao: SqlUserDao,
    accountDao: SqlAccountDao,
    ledgerDao: SqlLedgerDao,
) {
    route("/admin") {
        install(AdminPlugin) { pluginUserDao = userDao }
        authenticate("session-cookie") {
            get("") {
                val user = call.attributes[KEY_ADMIN_USER]
                val map = call.attributes[KEY_MAP]
                map["user"] = user
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/index.html.peb", map))
            }
            get("/createaccount") {
                val user = call.attributes[KEY_ADMIN_USER]
                val map = call.attributes[KEY_MAP]
                map["user"] = user
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/createaccount.html.peb", map))
            }
            post("/createaccount") {
                val user = call.attributes[KEY_ADMIN_USER]

                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters["csrf"])) return@post
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

                val map = call.attributes[KEY_MAP]
                map["user"] = user
                if (createUser) {
                    map["message"] = "account_user_created"
                    map["userId"] = userId
                } else {
                    map["message"] = "account_created"
                }
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/post_createaccount.html.peb", map))
            }
            get("/users") {
                val adminUser = call.attributes[KEY_ADMIN_USER]

                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                map["users"] = userDao.getAllUsers()
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/users.html.peb", map))
            }
            get("/accounts") {
                val adminUser = call.attributes[KEY_ADMIN_USER]

                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                map["accounts"] = accountDao.getAllAccountsAndUser()
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/accounts.html.peb", map))
            }
            get("/reconcile") {
                val adminUser = call.attributes[KEY_ADMIN_USER]

                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                map["transaction"] = ledgerDao.reconcileTransactionType()
                map["account"] = ledgerDao.reconcileAccountType()
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/reconcile.html.peb", map))
            }
            get("/withdraw") {
                val adminUser = call.attributes[KEY_ADMIN_USER]
                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/withdraw.html.peb", map))
            }
            get("/deposit") {
                val adminUser = call.attributes[KEY_ADMIN_USER]
                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/deposit.html.peb", map))
            }
            post("/withdraw/submit") {
                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters["csrf"])) return@post
                val accountCode = parameters["account"]
                val reason = parameters["reason"]
                val amountStr = parameters["amount"]
                val description = parameters["description"]

                val amountDec = amountStr?.toBigDecimalOrNull()

                val map = call.attributes[KEY_MAP]
                map["user"] = call.attributes[KEY_ADMIN_USER]

                if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                    || reason == null || reason !in listOf("teller", "other")
                    || description == null || !descriptionRegex.matcher(description).matches()
                    || accountCode == null || !codeRegex.matcher(accountCode).matches()
                    || amountDec == null || amountDec == BigDecimal.ZERO
                ) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val account = accountDao.readByCode(accountCode)
                if (account == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val method = when (reason) {
                    "teller" -> "Withdrawal through a teller"
                    "other" -> description
                    else -> throw IllegalArgumentException()
                }

                if (!ledgerDao.ledge(account.id, accountDao.assetAccount, amountStr, method)) {
                    map["error"] = "funds"
                    call.respond(HttpStatusCode.Conflict, PebbleContent("pages/admin/withdraw-submit.html.peb", map))
                    return@post
                }

                map["from"] = account
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/withdraw-submit.html.peb", map))

            }
            post("/deposit/submit") {
                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters["csrf"])) return@post
                val accountCode = parameters["account"]
                val reason = parameters["reason"]
                val amountStr = parameters["amount"]
                val description = parameters["description"]

                val amountDec = amountStr?.toBigDecimalOrNull()

                val map = call.attributes[KEY_MAP]
                map["user"] = call.attributes[KEY_ADMIN_USER]

                if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                    || reason == null || reason !in listOf("teller", "other")
                    || description == null || !descriptionRegex.matcher(description).matches()
                    || accountCode == null || !codeRegex.matcher(accountCode).matches()
                    || amountDec == null || amountDec == BigDecimal.ZERO
                ) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val account = accountDao.readByCode(accountCode)
                if (account == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val method = when (reason) {
                    "teller" -> "Deposit through a teller"
                    "other" -> description
                    else -> throw IllegalArgumentException()
                }

                if (!ledgerDao.ledge(accountDao.assetAccount, account.id, amountStr, method, true)) {
                    map["error"] = "funds"
                    call.respond(HttpStatusCode.Conflict, PebbleContent("pages/admin/deposit-submit.html.peb", map))
                    return@post
                }

                map["from"] = account
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/deposit-submit.html.peb", map))

            }
            get("/transfer") {
                val adminUser = call.attributes[KEY_ADMIN_USER]
                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/transfer.html.peb", map))
            }
            post("/transfer/submit") {
                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters["csrf"])) return@post
                val fromId = parameters["from"]?.toLongOrNull()
                val toId = parameters["to"]?.toLongOrNull()
                val amountStr = parameters["amount"]
                val description = parameters["description"]

                val amountDec = amountStr?.toBigDecimalOrNull()

                val map = call.attributes[KEY_MAP]
                map["user"] = call.attributes[KEY_ADMIN_USER]

                if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                    || description == null || !descriptionRegex.matcher(description).matches()
                    || fromId == null
                    || toId == null
                    || amountDec == null || amountDec == BigDecimal.ZERO
                ) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val toAccount = accountDao.read(toId)
                val fromAccount = accountDao.read(fromId)
                if (toAccount == null || fromAccount == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (!ledgerDao.ledge(fromId, toId, amountStr, description, true)) {
                    map["error"] = "funds"
                    call.respond(HttpStatusCode.Conflict, PebbleContent("pages/admin/transfer-submit.html.peb", map))
                    return@post
                }

                map["from"] = fromAccount
                map["to"] = toAccount
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/transfer-submit.html.peb", map))

            }
        }
        authenticate("session-cookie") {
            install(UserPlugin) { pluginUserDao = userDao }
            get("/user/{id}") {
                val user = call.attributes[KEY_READ_USER]
                val adminUser = call.attributes[KEY_ADMIN_USER]
                val accounts = accountDao.getAccounts(user.id)

                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                map["read_user"] = user
                map["accounts"] = accounts
                map["balances"] = ledgerDao.getBalances(accounts.map { it.id })
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/user.html.peb", map))
            }
            get("/user/{id}/editign") {
                val user = call.attributes[KEY_READ_USER]
                val map = call.attributes[KEY_MAP]
                map["read_user"] = user
                call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign_form.html.peb", map))
            }
            put("/user/{id}/editign") {
                val readUser = call.attributes[KEY_READ_USER]

                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters["csrf"])) return@put
                val ign = parameters["ign"]
                if (ign.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                val user = userDao.updateUserById(readUser.id, UpdateTargets(
                    IGN_COLUMN.withValue(ign)
                ))
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }

                val map = call.attributes[KEY_MAP]
                map["read_user"] = user
                call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign.html.peb", map))
            }

            get("/user/{id}/readeditign") {
                val user = call.attributes[KEY_READ_USER]
                val map = call.attributes[KEY_MAP]
                map["read_user"] = user
                call.respond(HttpStatusCode.OK, PebbleContent("snippets/editign.html.peb", map))
            }
        }
        authenticate("session-cookie") {
            install(AdminAccountPlugin) { pluginAccountDao = accountDao }

            get("/account/{id}") {
                val account = call.attributes[KEY_ADMIN_ACCOUNT]
                val transactions = ledgerDao.getTransactions(account.id)
                val map = call.attributes[KEY_MAP]
                map["account"] = account
                map["transactions"] = transactions
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/account.html.peb", map))
            }
        }
    }
}
