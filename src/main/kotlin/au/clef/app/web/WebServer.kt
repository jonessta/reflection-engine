package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.app.demo.model.AcmeService
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import au.clef.metadata.model.MetadataRoot
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.*

fun Application.module() {
    install(CORS) {
        allowHost("localhost:63342", schemes = listOf("http"))
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }
}

class WebServer {

    fun start() {
        val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")
        val engine = ReflectionEngine(
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )
        val instanceRegistry = InstanceRegistry(
            mapOf("acmeService" to AcmeService())
        )
        val valueMapper = ValueMapper(instanceRegistry)
        val api = ReflectionServiceApi(engine, instanceRegistry, valueMapper)

        embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) { json() }

            routing {
                get("/methods/{className}") {
                    val className: String = call.parameters["className"] ?: throw RuntimeException("Missing className")
                    val clazz: Class<*> = Class.forName(className)
                    val descriptors: List<MethodDescriptor> = engine.descriptors(clazz)
                    call.respond(descriptors)
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