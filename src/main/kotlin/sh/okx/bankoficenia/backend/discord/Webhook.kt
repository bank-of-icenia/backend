package sh.okx.bankoficenia.backend.discord

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*


private fun filterJson(string: String?): String {
    if (string == null) {
        return ""
    }
    return string.replace("\\", "").replace("\"", "")
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
        setBody(
            """{
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
}"""
        )
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
        setBody(
            """{
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
}"""
        )
    }
    return res.status == HttpStatusCode.OK
}
