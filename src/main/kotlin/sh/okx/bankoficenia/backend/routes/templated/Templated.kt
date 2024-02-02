package sh.okx.bankoficenia.backend.routes.templated

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlUnbankedDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.discord.notifyDeposit
import sh.okx.bankoficenia.backend.discord.notifyWithdrawal
import sh.okx.bankoficenia.backend.model.UserSession
import sh.okx.bankoficenia.backend.plugin.AccountPlugin
import sh.okx.bankoficenia.backend.plugin.KEY_ACCOUNT
import sh.okx.bankoficenia.backend.plugin.KEY_MAP
import sh.okx.bankoficenia.backend.plugin.KEY_USER
import sh.okx.bankoficenia.backend.plugins.validateCsrf
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.regex.Pattern

val amountFormat = DecimalFormat("0.0000")
val amountRegex: Pattern = Pattern.compile("\\d{0,10}(\\.\\d{1,4})?")
val descriptionRegex: Pattern = Pattern.compile("[ ,.!\"'$()?\\-_=+&*^%;:/0-9A-z]{0,32}")
val codeRegex: Pattern = Pattern.compile("\\d\\d-\\d\\d-\\d\\d")
val ignRegex: Pattern = Pattern.compile("\\w{1,16}")

fun Route.templatedRoutes(
    userDao: SqlUserDao,
    accountDao: SqlAccountDao,
    ledgerDao: SqlLedgerDao,
    unbankedDao: SqlUnbankedDao,
    client: HttpClient,
    webhook: String,
) {
    authenticate("session-cookie", optional = true) {
        get("/") {
            val user = call.principal<UserSession>()?.let { userDao.getUserById(it.userId) }

            val map = call.attributes[KEY_MAP]
            if (user != null) {
                map["user"] = user
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/index.html.peb", map))
        }
        get("/directory") {
            val user = call.principal<UserSession>()?.let { userDao.getUserById(it.userId) }

            val map = call.attributes[KEY_MAP]
            if (user != null) {
                map["user"] = user
            }
            map["directory"] = accountDao.getDirectoryAccounts()
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/directory.html.peb", map))
        }
        get("/tos") {
            val user = call.principal<UserSession>()?.let { userDao.getUserById(it.userId) }
            val map = call.attributes[KEY_MAP]
            if (user != null) {
                map["user"] = user
            }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/tos.html.peb", map))
        }
    }
    authenticate("session-cookie") {
        get("/accounts") {
            val user = call.principal<UserSession>()?.let { userDao.getUserById(it.userId) }
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val map = call.attributes[KEY_MAP]
            val accounts = accountDao.getAccounts(user.id)
            map["accounts"] = accounts
            map["balances"] = ledgerDao.getBalances(accounts.map { it.id })
                .mapIndexed { i, s -> if (accounts[i].accountType.isNormalDebit() || s == 0.0) s else -s}
                .map { String.format("%.4f", it) }
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

            val map = call.attributes[KEY_MAP]

            map["user"] = call.attributes[KEY_USER]
            map["account"] = call.attributes[KEY_ACCOUNT]
            map["transactions"] = transactions
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/index.html.peb", map))
        }
        post("/account/{id}/showindirectory") {
            val account = call.attributes[KEY_ACCOUNT]

            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post

            if (parameters["directorycheckbox"] == "on") {
                accountDao.setInDirectory(account.id, true)
            } else if (parameters["directorycheckbox"] == null) {
                accountDao.setInDirectory(account.id, false)
            } else {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
    authenticate("session-cookie") {
        install(AccountPlugin) {
            pluginUserDao = userDao
            pluginAccountDao = accountDao
            optionalAccount = true
        }
        get("/transfer") {
            val map = call.attributes[KEY_MAP]
            map["user"] = call.attributes[KEY_USER]
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            map["accounts"] = accountDao.getAccounts(call.attributes[KEY_USER].id)
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/transfer.html.peb", map))
        }
        get("/deposit") {
            val map = call.attributes[KEY_MAP]
            val user = call.attributes[KEY_USER]
            map["user"] = user
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            map["accounts"] = accountDao.getAccounts(user.id)
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/deposit.html.peb", map))
        }
        get("/withdraw") {
            val map = call.attributes[KEY_MAP]
            val user = call.attributes[KEY_USER]
            map["user"] = user
            if (call.attributes.contains(KEY_ACCOUNT)) {
                map["account"] = call.attributes[KEY_ACCOUNT]
            }
            map["accounts"] = accountDao.getAccounts(user.id)
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/withdraw.html.peb", map))
        }
        post("/transfer/confirm") {
            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post

            val fromId = parameters["from"]
            val toCode = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = call.attributes[KEY_MAP]
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

            val account = accountDao.readByCode(fromId)
            if (account?.code == null || account.userId != call.attributes[KEY_USER].id) {
                map["error"] = "generic"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            val toAcc = accountDao.readByCode(toCode)
            if (toAcc == null) {
                map["error"] = "account_does_not_exist"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            if (toAcc.id == account.id) {
                map["error"] = "self_transfer"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/transfer-confirm.html.peb", map))
                return@post
            }

            var balance = ledgerDao.getBalances(listOf(account.id))[0]
            if (!account.accountType.isNormalDebit() && balance != 0.0) {
                balance = -balance;
            }
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
            if (!validateCsrf(call, parameters["csrf"])) return@post
            val fromId = parameters["from"]
            val toCode = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = call.attributes[KEY_MAP]
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
            if (account == null || account.userId != call.attributes[KEY_USER].id) {
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
        post("/unbanked-transfer/confirm") {
            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post

            val fromId = parameters["from"]
            val toIgn = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = call.attributes[KEY_MAP]
            map["user"] = call.attributes[KEY_USER]

            if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                || description == null || !descriptionRegex.matcher(description).matches()
                || toIgn == null || !ignRegex.matcher(toIgn).matches()
                || fromId == null
                || amountDec == null
                || amountDec == BigDecimal.ZERO
            ) {
                map["error"] = "generic"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/unbanked-transfer-confirm.html.peb", map))
                return@post
            }

            if (userDao.hasIgn(toIgn)) {
                map["error"] = "ign_exists"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/unbanked-transfer-confirm.html.peb", map))
                return@post
            }

            val account = accountDao.readByCode(fromId)
            if (account?.code == null || account.userId != call.attributes[KEY_USER].id) {
                map["error"] = "generic"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/unbanked-transfer-confirm.html.peb", map))
                return@post
            }

            var balance = ledgerDao.getBalances(listOf(account.id))[0]
            if (!account.accountType.isNormalDebit() && balance != 0.0) {
                balance = -balance
            }
            // Not a safe comparison but this isn't the real one
            if (amountDec > BigDecimal.valueOf(balance)) {
                map["error"] = "funds"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/unbanked-transfer-confirm.html.peb", map))
                return@post
            }

            map["from"] = account.code
            map["to"] = toIgn
            map["amount"] = amountFormat.format(amountDec)
            map["description"] = description
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/unbanked-transfer-confirm.html.peb", map))
        }

        post("/unbanked-transfer/submit") {
            // The user should not have changed any of these parameters so we don't need to bother with a good page
            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post
            val fromId = parameters["from"]
            val toIgn = parameters["to"]
            val amountStr = parameters["amount"]
            val description = parameters["description"]

            val amountDec = amountStr?.toBigDecimalOrNull()

            val map = call.attributes[KEY_MAP]
            map["user"] = call.attributes[KEY_USER]

            if (amountStr == null || !amountRegex.matcher(amountStr).matches()
                || description == null || !descriptionRegex.matcher(description).matches()
                || toIgn == null || !ignRegex.matcher(toIgn).matches()
                || fromId == null || !codeRegex.matcher(fromId).matches()
                || amountDec == null
                || amountDec == BigDecimal.ZERO
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (userDao.hasIgn(toIgn)) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val account = accountDao.readByCode(fromId)
            if (account == null  || account.userId != call.attributes[KEY_USER].id) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (!unbankedDao.ledgeUnbanked(account.id, accountDao.unbankedLiabilitiesAccount, amountStr, "Unbanked transfer to $toIgn: $description", toIgn)) {
                map["error"] = "funds"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/unbanked-transfer-submit.html.peb", map))
                return@post
            }

            map["from"] = account
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/unbanked-transfer-submit.html.peb", map))
        }
        post("/deposit/submit") {
            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post
            val fromCode = parameters["from"]
            val toMethod = parameters["to"]
            val info = parameters["info"]

            val map = call.attributes[KEY_MAP]
            map["user"] = call.attributes[KEY_USER]

            if (fromCode == null
                || toMethod == null || toMethod !in listOf("branch", "inperson", "dropchest", "other")
                || info == null
            ) {
                map["message"] = "invalid"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/deposit-submit.html.peb", map))
                return@post
            }

            val account = accountDao.readByCode(fromCode)
            if (account == null || account.userId != call.attributes[KEY_USER].id) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val user = userDao.getUserById(account.userId)

            val method = convertMethod(toMethod)

            if (!notifyDeposit(client, webhook, fromCode, info, method, user?.ign, user?.discordGlobalname)) {
                map["message"] = "discord_failed"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/deposit-submit.html.peb", map))
                return@post
            }

            map["message"] = "success"
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/deposit-submit.html.peb", map))
        }
        post("/withdraw/submit") {
            val parameters = call.receiveParameters()
            if (!validateCsrf(call, parameters["csrf"])) return@post
            val fromCode = parameters["from"]
            val toMethod = parameters["to"]
            val info = parameters["info"]
            val amount = parameters["amount"]?.toLongOrNull()

            val map = call.attributes[KEY_MAP]
            map["user"] = call.attributes[KEY_USER]

            if (fromCode == null
                || toMethod == null || toMethod !in listOf("branch", "inperson", "dropchest", "other")
                || info == null
                || amount == null
            ) {
                map["message"] = "invalid"
                call.respond(HttpStatusCode.BadRequest, PebbleContent("pages/account/withdraw-submit.html.peb", map))
                return@post
            }

            val account = accountDao.readByCode(fromCode)
            if (account == null || account.userId != call.attributes[KEY_USER].id) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val user = userDao.getUserById(account.userId)

            val method = convertMethod(toMethod)

            if (!notifyWithdrawal(
                    client,
                    webhook,
                    fromCode,
                    amount.toString(),
                    info,
                    method,
                    user?.ign,
                    user?.discordGlobalname
                )
            ) {
                map["message"] = "discord_failed"
                call.respond(HttpStatusCode.Conflict, PebbleContent("pages/account/withdraw-submit.html.peb", map))
                return@post
            }

            map["message"] = "success"
            call.respond(HttpStatusCode.OK, PebbleContent("pages/account/withdraw-submit.html.peb", map))
        }
    }
}

private fun convertMethod(method: String): String {
    return when (method) {
        "branch" -> "Meet in a bank branch"
        "inperson" -> "Meet elsewhere"
        "dropchest" -> "Drop-chest near Icenia City"
        "other" -> "Other"
        else -> throw IllegalArgumentException()
    }
}
