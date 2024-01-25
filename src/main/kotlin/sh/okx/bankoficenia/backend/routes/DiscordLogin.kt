package sh.okx.bankoficenia.backend.routes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.model.UserSession
import java.security.SecureRandom

@OptIn(ExperimentalStdlibApi::class)
fun Route.discordLoginRoute(httpClient: HttpClient, sessionDao: SqlSessionDao, userDao: SqlUserDao) {
    val csprng = SecureRandom()
    authenticate("auth-oauth-discord") {
        get("/login") {}
        get("/callback") {
            val currentPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
            // redirects home if the url is not found before authorization
            currentPrincipal?.let { principal ->
                principal.state?.let { state ->
                    val discordUser = getDiscordUser(httpClient, principal.accessToken)
                    val bytes = ByteArray(32)
                    csprng.nextBytes(bytes)
                    val sessionId = bytes.toHexString()
                    val userId = userDao.getOrCreateUser(discordUser.id, discordUser.username, discordUser.globalname)
                    if (userId != null) {
                        sessionDao.write(sessionId, userId)
                        call.sessions.set(sessionId)
                    }
                }
            }
            call.respondRedirect("/")
        }
    }
    authenticate("session-cookie", optional = true) {
        get("/logout") {
            call.sessions.get("id")?.let { sessionDao.invalidate(it as String) }
            call.respond(HttpStatusCode.OK, PebbleContent("pages/logout.html.peb", mapOf()))
        }
    }
}

private suspend fun getDiscordUser(
    httpClient: HttpClient,
    bearer: String
): UserInfo = httpClient.get("https://discord.com/api/v10/users/@me") {
    headers {
        append(HttpHeaders.Authorization, "Bearer $bearer")
    }
}.body()

@Serializable
internal data class UserInfo(
    val id: Long,
    val username: String,
    @SerialName("global_name") val globalname: String,
)