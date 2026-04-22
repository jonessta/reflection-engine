package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.model.InvocationRequest
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import au.clef.metadata.model.MetadataRoot
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

@Serializable
data class InvocationResponse(
    val result: String?
)

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

private fun MethodDescriptor.toResponse(): MethodDescriptorResponse = MethodDescriptorResponse(
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

    fun start() {
        val metadata: MetadataRoot =
            MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")

        val methodRegistry = MethodRegistry(
            AcmeService::class,
            Math::class,
            Person::class,
            Address::class
        )

        val engine = ReflectionEngine(
            methodRegistry = methodRegistry,
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )

        val instanceRegistry = InstanceRegistry(
            mapOf("acmeService" to AcmeService())
        )

        val api = ReflectionServiceApi(engine = engine, instanceRegistry = instanceRegistry)

        embeddedServer(Netty, port = 8080) {
            install(CORS) {
                allowHost("localhost:63342", schemes = listOf("http"))
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
            }

            install(ContentNegotiation) {
                json(
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        classDiscriminator = "kind"
                    }
                )
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
                    } catch (_: ClassNotFoundException) {
                        call.respond(HttpStatusCode.NotFound, "Class not found: $className")
                        return@get
                    }
                    val descriptors: List<MethodDescriptor> = engine.descriptors(clazz)
                    val response: List<MethodDescriptorResponse> = descriptors.map { descriptor: MethodDescriptor ->
                        descriptor.toResponse()
                    }
                    call.respond(response)
                }

                post("/invoke") {
                    try {
                        val request: InvocationRequest = call.receive()
                        println("INVOKE REQUEST = $request")

                        val result: Any? = api.invoke(request)

                        call.respond(
                            HttpStatusCode.OK,
                            InvocationResponse(
                                result = result?.toString()
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.BadRequest,
                            InvocationResponse(
                                result = "ERROR: ${e.message}"
                            )
                        )
                    }
                }
            }
        }.start(wait = true)
    }
}