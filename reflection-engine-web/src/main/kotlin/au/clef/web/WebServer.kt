package au.clef.web

import au.clef.api.ReflectionApiConfig
import au.clef.api.ReflectionServiceApi
import au.clef.api.model.InvocationRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun Application.configureJson(
    reflectionServiceApi: ReflectionServiceApi
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = false
                prettyPrint = true
                classDiscriminator = "kind"
                serializersModule = reflectionServiceApi.serializersModule()
            }
        )
    }
}

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()

            val response = when (cause) {
                is IllegalArgumentException -> ErrorResponse(error = buildErrorMessage(cause))

                else -> ErrorResponse(error = buildErrorMessage(cause))
            }

            val status = when (cause) {
                is IllegalArgumentException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.InternalServerError
            }

            call.respond(status, response)
        }
    }
}

private fun buildErrorMessage(throwable: Throwable): String =
    buildString {
        append(throwable.message ?: throwable::class.simpleName ?: "Unknown error")

        var cause: Throwable? = throwable.cause
        while (cause != null) {
            append("\nCaused by: ")
            append(cause.message ?: cause::class.simpleName ?: "Unknown cause")
            cause = cause.cause
        }
    }

data class WebServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0"
)

class WebServer(apiConfig: ReflectionApiConfig, private val webConfig: WebServerConfig = WebServerConfig()) {

    private val reflectionServiceApi: ReflectionServiceApi = ReflectionServiceApi(apiConfig)

    fun start() {
        embeddedServer(
            Netty,
            host = webConfig.host,
            port = webConfig.port
        ) {
            configureJson(reflectionServiceApi)

            configureErrorHandling()

            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowNonSimpleContentTypes = true
            }

            routing {
                reflectionRoutes(reflectionServiceApi)
            }
        }.start(wait = true)
    }
}

@Serializable
private data class ErrorResponse(
    val error: String
)

fun Route.reflectionRoutes(
    reflectionService: ReflectionServiceApi
) {
    get("/health") {
        call.respond(mapOf("ok" to true))
    }

    get("/executions") {
        call.respond(reflectionService.executionDescriptors())
    }

    post("/invoke") {
        val request: InvocationRequest = call.receive()
        val response = reflectionService.invoke(request)
        call.respond(response)
    }
}