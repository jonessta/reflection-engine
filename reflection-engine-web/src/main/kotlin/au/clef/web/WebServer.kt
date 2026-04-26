package au.clef.web

import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

data class WebServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0"
)

class WebServer(
    private val reflectionService: ReflectionServiceApi,
    private val config: WebServerConfig = WebServerConfig()
) {
    fun start() {
        embeddedServer(Netty, host = config.host, port = config.port) {
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
                allowHeader(io.ktor.http.HttpHeaders.ContentType)
                allowMethod(io.ktor.http.HttpMethod.Get)
                allowMethod(io.ktor.http.HttpMethod.Post)
                allowNonSimpleContentTypes = true
            }

            routing {
                get("health") {
                    call.respond(mapOf("ok" to true))
                }
                reflectionRoutes(reflectionService)
            }
        }.start(wait = true)
    }
}

@kotlinx.serialization.Serializable
private data class ErrorResponse(val error: String)

fun Route.reflectionRoutes(reflectionService: ReflectionServiceApi) {
    get("executions") {
        val body = call.receiveText()
        println("INVOKE BODY = $body")
        call.respond(reflectionService.executionDescriptors())
    }
    post("invoke") {
        try {
            val request: InvocationRequest = call.receive()
            val response: InvocationResponse = reflectionService.invoke(request)
            call.respond(response)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Bad request"))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(e.message ?: e::class.simpleName ?: "Invocation failed")
            )
        }
    }
}