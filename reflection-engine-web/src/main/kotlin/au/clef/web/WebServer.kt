package au.clef.web

import au.clef.api.ReflectionApiConfig
import au.clef.api.ReflectionServiceApi
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.engine.ReflectionConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class WebServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0"
)

class WebServer(
    private val apiConfig: ReflectionApiConfig,
    private val webConfig: WebServerConfig = WebServerConfig()
) {
    constructor(
        reflectionConfig: ReflectionConfig,
        webConfig: WebServerConfig = WebServerConfig()
    ) : this(
        apiConfig = ReflectionApiConfig(reflectionConfig),
        webConfig = webConfig
    )

    private val reflectionServiceApi = ReflectionServiceApi(apiConfig)

    fun start() {
        embeddedServer(
            Netty,
            host = webConfig.host,
            port = webConfig.port
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        classDiscriminator = "kind"
                    }
                )
            }

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
private data class ErrorResponse(val error: String)

fun Route.reflectionRoutes(reflectionService: ReflectionServiceApi) {
    get("/health") {
        call.respond(mapOf("ok" to true))
    }

    get("/executions") {
        call.respond(reflectionService.executionDescriptors())
    }

    post("/invoke") {
        try {
            val request: InvocationRequest = call.receive()
            val response: InvocationResponse = reflectionService.invoke(request)
            call.respond(response)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(e.message ?: "Bad request")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(e.message ?: e::class.simpleName ?: "Invocation failed")
            )
        }
    }
}