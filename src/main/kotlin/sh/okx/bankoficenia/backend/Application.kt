package sh.okx.bankoficenia.backend

import com.typesafe.config.ConfigFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import sh.okx.bankoficenia.backend.database.*
import sh.okx.bankoficenia.backend.model.UserSession
import sh.okx.bankoficenia.backend.plugins.Extensions
import sh.okx.bankoficenia.backend.plugins.configureRouting
import java.io.File
import javax.sql.DataSource

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.parseFile(File("backend.conf")))
    val dataSource = getDataSource(config)
    Database.connect(dataSource)
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = { module(config = config, dataSource = dataSource) })
        .start(wait = true)
}

val applicationHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

fun Application.module(httpClient: HttpClient = applicationHttpClient, config: HoconApplicationConfig, dataSource: DataSource) {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
        extension(Extensions())
    }

    install(Sessions) {
        cookie<String>("id") {
            // The default serializer doesn't work properly with regular strings for some reason
            serializer = object : SessionSerializer<String> {
                override fun deserialize(text: String): String {
                    return text
                }

                override fun serialize(session: String): String {
                    return session
                }
            }
            cookie.httpOnly = true
            // TODO cookie secure
            // TODO cookie domain
//            cookie.maxAgeInSeconds = // 7 days default
        }
    }
    val sessionDao = SqlSessionDao(dataSource)

    install(Authentication) {
        oauth("auth-oauth-discord") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "discord",
                    authorizeUrl = "https://discord.com/oauth2/authorize",
                    accessTokenUrl = "https://discord.com/api/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = config.property("discord.client_id").getString(),
                    clientSecret = config.property("discord.client_secret").getString(),
                    defaultScopes = listOf("identify"),
                    extraAuthParameters = listOf("access_type" to "online", "prompt" to "none"),
                    onStateCreated = { call, state -> {} }
                )
            }
            client = httpClient
        }
        session<String>("session-cookie") {
            validate { session ->
                sessionDao.read(session)
            }
            challenge {
            }
        }
    }
    configureRouting(httpClient, sessionDao, SqlUserDao(dataSource), SqlAccountDao(dataSource), SqlLedgerDao(dataSource))
}
