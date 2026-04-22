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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val METADATA_PATH = "/config/method-metadata.json"
private const val DEFAULT_PORT = 8080

private val REGISTERED_CLASSES = listOf(
    AcmeService::class,
    Math::class,
    Person::class,
    Address::class
)

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

class WebServer {

    fun start(port: Int = DEFAULT_PORT) {
        val api = createApi()
        embeddedServer(Netty, port = port) {
            configureHttp()
            configureRoutes(api)
        }.start(wait = true)
    }

    private fun createApi(): ReflectionServiceApi {
        val methodRegistry = MethodRegistry(*REGISTERED_CLASSES.toTypedArray())
        val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(METADATA_PATH)

        val engine = ReflectionEngine(
            methodRegistry = methodRegistry,
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )

        val instanceRegistry = InstanceRegistry(
            mapOf("acmeService" to AcmeService())
        )

        return ReflectionServiceApi(engine, instanceRegistry)
    }
}

private fun Application.configureHttp() {
    install(CORS) {
        allowHost("localhost:63342", schemes = listOf("http"))
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
                val descriptors = api.descriptors(className)
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