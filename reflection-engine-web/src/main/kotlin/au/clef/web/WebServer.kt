package au.clef.web

import au.clef.api.DefaultClassResolver
import au.clef.api.ReflectionApiConfig
import au.clef.api.ReflectionServiceApi
import au.clef.api.ScalarTypeRegistry
import au.clef.api.model.InvocationRequest
import au.clef.api.model.valueSerializersModule
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.registry.MethodSourceTypes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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

fun Application.configureJson(
    methodSourceTypes: MethodSourceTypes,
    scalarTypeRegistry: ScalarTypeRegistry
) {
    val classResolver = DefaultClassResolver(
        methodSourceTypes = methodSourceTypes,
        scalarRegistry = scalarTypeRegistry
    )

    val valueModule = valueSerializersModule(
        classResolver = classResolver,
        scalarTypeRegistry = scalarTypeRegistry
    )

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = false
                prettyPrint = true
                classDiscriminator = "kind"
                serializersModule = valueModule
            }
        )
    }
}

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

    private val reflectionServiceApi: ReflectionServiceApi =
        ReflectionServiceApi(apiConfig)

    private val methodSourceTypes: MethodSourceTypes =
        ReflectionEngine(apiConfig.reflectionConfig)

    fun start() {
        embeddedServer(
            Netty,
            host = webConfig.host,
            port = webConfig.port
        ) {
            configureJson(
                methodSourceTypes = methodSourceTypes,
                scalarTypeRegistry = apiConfig.scalarTypeRegistry
            )

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
        try {
            val request: InvocationRequest = call.receive()
            val response = reflectionService.invoke(request)
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