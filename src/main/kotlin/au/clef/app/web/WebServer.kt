package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.app.demo.model.AcmeService
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import au.clef.metadata.model.MetadataRoot
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
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class MethodDescriptorResponse(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptorResponse>,
    val returnType: String,
    val isStatic: Boolean
)

@Serializable
data class ParamDescriptorResponse(
    val index: Int,
    val type: String,
    val reflectedName: String,
    val name: String,
    val label: String? = null,
    val nullable: Boolean
)

private fun MethodDescriptor.toResponse(): MethodDescriptorResponse =
    MethodDescriptorResponse(
        id = id.toString(),
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

class WebServer {

    fun start(): Unit {
        val metadata: MetadataRoot =
            MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")

        val engine: ReflectionEngine =
            ReflectionEngine(
                metadataRegistry = DescriptorMetadataRegistry(metadata)
            )

        val instanceRegistry: InstanceRegistry =
            InstanceRegistry(
                mapOf(
                    "acmeService" to AcmeService()
                )
            )

        val valueMapper: ValueMapper = ValueMapper(instanceRegistry)

        val api: ReflectionServiceApi =
            ReflectionServiceApi(
                engine = engine,
                instanceRegistry = instanceRegistry,
                valueMapper = valueMapper
            )

        embeddedServer(Netty, port = 8080) {
            install(CORS) {
                allowHost("localhost:63342", schemes = listOf("http"))
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
            }

            install(ContentNegotiation) {
                json()
            }

            routing {
                get("/methods/{className}") {
                    val className: String? = call.parameters["className"]

                    if (className == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing className")
                        return@get
                    }

                    val clazz: Class<*> = try {
                        Class.forName(className)
                    } catch (e: ClassNotFoundException) {
                        call.respond(HttpStatusCode.NotFound, "Class not found: $className")
                        return@get
                    }

                    val descriptors: List<MethodDescriptor> = engine.descriptors(clazz)

                    val response: List<MethodDescriptorResponse> =
                        descriptors.map { descriptor: MethodDescriptor ->
                            descriptor.toResponse()
                        }

                    call.respond(response)
                }

                post("/invoke") {
                    val request: InvocationRequest = call.receive()
                    val result: Any? = api.invoke(request)
                    call.respond(mapOf("result" to result))
                }
            }
        }.start(wait = true)
    }
}