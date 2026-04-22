package au.clef.app.web

import au.clef.api.model.InvocationRequest
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class InvocationResponse(
    val result: String?
)

@Serializable
private data class MethodDescriptorResponse(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptorResponse>,
    val returnType: String,
    val isStatic: Boolean
)

@Serializable
private data class ParamDescriptorResponse(
    val index: Int,
    val type: String,
    val reflectedName: String,
    val name: String,
    val label: String? = null,
    val nullable: Boolean
)

private fun MethodDescriptor.toResponse(): MethodDescriptorResponse =
    MethodDescriptorResponse(
        id = id.value,
        name = reflectedName,
        displayName = displayName,
        parameters = parameters.map { param: ParamDescriptor ->
            ParamDescriptorResponse(
                index = param.index,
                type = param.type.name,
                reflectedName = param.reflectedName,
                name = param.name,
                label = param.label,
                nullable = param.nullable
            )
        },
        returnType = returnType.name,
        isStatic = isStatic
    )

class WebServer(
    private val api: ReflectionServiceApi,
    private val config: WebServerConfig = WebServerConfig()
) {
    fun start() {
        embeddedServer(Netty, port = config.port) {
            configureHttp()
            configureRoutes(api)
        }.start(wait = true)
    }

    private fun Application.configureHttp() {
        install(CORS) {
            allowHost(config.corsHost, schemes = listOf(config.corsScheme))
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    classDiscriminator = "kind"
                }
            )
        }
    }

    private fun Application.configureRoutes(api: ReflectionServiceApi) {
        routing {
            get("/methods/{className}") {
                val className = call.parameters["className"]
                if (className == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing className")
                    return@get
                }

                try {
                    val descriptors: List<MethodDescriptor> = api.descriptors(className)
                    call.respond(descriptors.map(MethodDescriptor::toResponse))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Unknown class")
                }
            }

            post("/invoke") {
                try {
                    val request: InvocationRequest = call.receive()
                    println("INVOKE REQUEST = $request")
                    val result = api.invoke(request)
                    call.respond(HttpStatusCode.OK, InvocationResponse(result?.toString()))
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        InvocationResponse("ERROR: ${e.message}")
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        InvocationResponse("ERROR: ${e.message}")
                    )
                }
            }
        }
    }
}