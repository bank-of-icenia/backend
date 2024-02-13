package sh.okx.bankoficenia.backend.routes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.security.SecureRandom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUserDao

@OptIn(ExperimentalStdlibApi::class)
fun Route.discordLoginRoute(
    httpClient: HttpClient,
    sessionDao: SqlSessionDao,
    userDao: SqlUserDao
) {
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
    @SerialName("global_name") val globalname: String?,
)
