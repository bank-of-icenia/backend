package sh.okx.bankoficenia.backend.discord

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

private fun filterJson(
    string: String?
): String {
    return string?.replace("\\", "")?.replace("\"", "") ?: ""
}

suspend fun notifyWithdrawal(
    client: HttpClient,
    webhook: String,
    code: String,
    amount: String,
    info: String,
    method: String,
    ign: String?,
    discord: String?
): Boolean {
    val res = client.post(webhook) {
        url {
            parameters.append("wait", "true")
        }
        contentType(ContentType.Application.Json)
        setBody("""
            {
                "content": null,
                "embeds": [
                    {
                        "title": "Withdrawal Request",
                        "color": 5814783,
                        "fields": [
                            {
                                "name": "Account",
                                "value": "${filterJson(code)}",
                                "inline": true
                            },
                            {
                                "name": "Amount",
                                "value": "${filterJson(amount)}d",
                                "inline": true
                            },
                            {
                                "name": "Additional information",
                                "value": "${filterJson(info)}"
                            },
                            {
                                "name": "Preferred method of pickup",
                                "value": "${filterJson(method)}"
                            },
                            {
                                "name": "IGN",
                                "value": "${filterJson(ign)}",
                                "inline": true
                            },
                            {
                                "name": "Discord name",
                                "value": "${filterJson(discord)}",
                                "inline": true
                            }
                        ]
                    }
                ],
                "username": "Bank of Icenia",
                "attachments": []
            }
        """.trimIndent())
    }
    return res.status == HttpStatusCode.OK
}

suspend fun notifyDeposit(
    client: HttpClient,
    webhook: String,
    code: String,
    info: String,
    method: String,
    ign: String?,
    discord: String?
): Boolean {
    val res = client.post(webhook) {
        url {
            parameters.append("wait", "true")
        }
        contentType(ContentType.Application.Json)
        setBody("""
            {
                "content": null,
                "embeds": [
                    {
                        "title": "Deposit Request",
                        "color": 5814783,
                        "fields": [
                            {
                                "name": "Account",
                                "value": "${filterJson(code)}",
                                "inline": true
                            },
                            {
                                "name": "Additional information",
                                "value": "${filterJson(info)}"
                            },
                            {
                                "name": "Preferred method of deposit",
                                "value": "${filterJson(method)}"
                            },
                            {
                                "name": "IGN",
                                "value": "${filterJson(ign)}",
                                "inline": true
                            },
                            {
                                "name": "Discord name",
                                "value": "${filterJson(discord)}",
                                "inline": true
                            }
                        ]
                    }
                ],
                "username": "Bank of Icenia",
                "attachments": [],
                "allowed_mentions": { "parse": [] }
            }
        """.trimIndent())
    }
    return res.status == HttpStatusCode.OK
}
