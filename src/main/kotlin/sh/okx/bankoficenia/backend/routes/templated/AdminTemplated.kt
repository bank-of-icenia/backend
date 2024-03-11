package sh.okx.bankoficenia.backend.routes.templated

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.math.BigDecimal
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.collections.set
import sh.okx.bankoficenia.backend.database.IGN_COLUMN
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.database.UpdateTargets
import sh.okx.bankoficenia.backend.database.banking
import sh.okx.bankoficenia.backend.database.calculateAccruedInterestFor
import sh.okx.bankoficenia.backend.plugin.AdminAccountPlugin
import sh.okx.bankoficenia.backend.plugin.AdminPlugin
import sh.okx.bankoficenia.backend.plugin.CSRF_KEY
import sh.okx.bankoficenia.backend.plugin.KEY_ADMIN_ACCOUNT
import sh.okx.bankoficenia.backend.plugin.KEY_ADMIN_USER
import sh.okx.bankoficenia.backend.plugin.KEY_MAP
import sh.okx.bankoficenia.backend.plugin.KEY_READ_USER
import sh.okx.bankoficenia.backend.plugin.UserPlugin
import sh.okx.bankoficenia.backend.plugins.validateCsrf

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
            post("/createaccount") {
                val user = call.attributes[KEY_ADMIN_USER]

                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@post
                val userId = parameters["user-id"]?.toLongOrNull()
                if (userId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PebbleContent(
                            "pages/admin/post_createaccount.html.peb",
                            mapOf("message" to "parameter_missing", "parameter" to "user-id")
                        )
                    )
                    return@post
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
                map["message"] = "account_created"
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
            get("/interest") {
                val adminUser = call.attributes[KEY_ADMIN_USER]

                val map = call.attributes[KEY_MAP]
                map["user"] = adminUser
                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/interest.html.peb", map))
            }
            post("/interest/early/submit") {
                val parameters = call.receiveParameters()
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@post

                val map = call.attributes[KEY_MAP]
                map["user"] = call.attributes[KEY_ADMIN_USER]

                data class InterestEarlyAccount(
                    val accountId: Long,
                    val ownerName: String,
                    val amount: String
                )

                val skipped: MutableSet<InterestEarlyAccount> = mutableSetOf()
                val failed: MutableSet<InterestEarlyAccount> = mutableSetOf()
                val success: MutableSet<InterestEarlyAccount> = mutableSetOf()

                for (account in accountDao.getAllAccountsAndUser()) {
                    if (account.code === null) {
                        continue
                    }
                    val interestAmount: BigDecimal
                    banking {
                        interestAmount = calculateAccruedInterestFor(account.id)
                    }
                    val sender: Long
                    val recipient: Long
                    if (interestAmount > BigDecimal.ZERO) {
                        sender = accountDao.interestAccount
                        recipient = account.id
                    }
                    else if (interestAmount < BigDecimal.ZERO) {
                        sender = account.id
                        recipient = accountDao.assetAccount
                    }
                    else {
                        skipped.add(InterestEarlyAccount(
                            account.id,
                            account.userIgn ?: account.userDiscord?.let { "d:$it" } ?: ("boi:" + account.id),
                            String.format("%.4f", BigDecimal.ZERO)
                        ))
                        continue
                    }
                    if (!ledgerDao.ledge(
                        sender,
                        recipient,
                        String.format("%.4f", interestAmount),
                        "Instant interest payout",
                        force = true
                    )) {
                        failed.add(InterestEarlyAccount(
                            account.id,
                            account.userIgn ?: account.userDiscord?.let { "d:$it" } ?: ("boi:" + account.id),
                            String.format("%.4f", BigDecimal.ZERO)
                        ))
                        continue
                    }
                    success.add(InterestEarlyAccount(
                        account.id,
                        account.userIgn ?: account.userDiscord?.let { "d:$it" } ?: ("boi:" + account.id),
                        String.format("%.4f", interestAmount)
                    ))
                }

                map["skipped"] = skipped
                map["failed"] = failed
                map["success"] = success

                call.respond(HttpStatusCode.OK, PebbleContent("pages/admin/interest_early_submit.html.peb", map))
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
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@post
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
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@post
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
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@post
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
                if (!validateCsrf(call, parameters[CSRF_KEY])) return@put
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
