package sh.okx.bankoficenia.backend.routes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.okx.bankoficenia.backend.model.UserSession
import sh.okx.bankoficenia.backend.model.getSessionApi

fun Route.userInfoRoute(httpClient: HttpClient) {
    authenticate("session-cookie") {
        get("/user") {
            val session = getSessionApi(call) ?: return@get
            call.respondText(getDiscordUser(httpClient, session).globalname)
        }
    }
}

private suspend fun getDiscordUser(
    httpClient: HttpClient,
    userSession: UserSession
): UserInfo2 = httpClient.get("https://discord.com/api/v10/users/@me") {
    headers {
        append(HttpHeaders.Authorization, "Bearer ${userSession.userId}")
    }
}.body()

@Serializable
internal data class UserInfo2(
    val id: String,
    val username: String,
    @SerialName("global_name") val globalname: String,
)