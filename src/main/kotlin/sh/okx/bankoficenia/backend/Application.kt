package sh.okx.bankoficenia.backend

import com.typesafe.config.ConfigFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.oauth
import io.ktor.server.auth.session
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.pebbletemplates.pebble.loader.ClasspathLoader
import java.io.File
import javax.sql.DataSource
import kotlin.collections.listOf
import kotlin.collections.set
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import sh.okx.bankoficenia.backend.database.SqlAccountDao
import sh.okx.bankoficenia.backend.database.SqlLedgerDao
import sh.okx.bankoficenia.backend.database.SqlSessionDao
import sh.okx.bankoficenia.backend.database.SqlUnbankedDao
import sh.okx.bankoficenia.backend.database.SqlUserDao
import sh.okx.bankoficenia.backend.database.constructDataSource
import sh.okx.bankoficenia.backend.plugins.Extensions
import sh.okx.bankoficenia.backend.plugins.configureRouting

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.parseFile(File("backend.conf")))
    val dataSource = constructDataSource(config)
    Database.connect(dataSource)
    embeddedServer(
        Netty,
        port = config.property("port").getString().toInt(),
        host = "127.0.0.1",
        module = { module(
            config = config,
            dataSource = dataSource
        ) }
    ).start(wait = true)
}

val applicationHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

fun Application.module(
    httpClient: HttpClient = applicationHttpClient,
    config: HoconApplicationConfig,
    dataSource: DataSource
) {
    install(Compression) {
        gzip {
            // Don't compress any sensitive data like the CSRF token because of the BREACH attack
            // But compressing CSS and JS is OK
            excludeContentType(ContentType.Text.Html)
        }
    }

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
            // cookie.secure doesn't work on localhost (because of silly ktor checks), but this does
            cookie.extensions["Secure"] = null
//            cookie.maxAgeInSeconds = // 7 days default
        }
    }
    val sessionDao = SqlSessionDao(dataSource)

    install(Authentication) {
        oauth("auth-oauth-discord") {
            urlProvider = { config.property("discord.post_auth_dest").getString() }
            providerLookup = { OAuthServerSettings.OAuth2ServerSettings(
                name = "discord",
                authorizeUrl = "https://discord.com/oauth2/authorize",
                accessTokenUrl = "https://discord.com/api/oauth2/token",
                requestMethod = HttpMethod.Post,
                clientId = config.property("discord.client_id").getString(),
                clientSecret = config.property("discord.client_secret").getString(),
                defaultScopes = listOf("identify"),
                extraAuthParameters = listOf("access_type" to "online", "prompt" to "none"),
                onStateCreated = { call, state -> {} }
            ) }
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
    configureRouting(
        httpClient,
        sessionDao,
        SqlUserDao(dataSource),
        SqlAccountDao(dataSource),
        SqlLedgerDao(dataSource),
        SqlUnbankedDao(dataSource),
        config.property("discord.webhook").getString(),
    )
}
