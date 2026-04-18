package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.app.demo.model.AcmeService
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/*
todo

Invoke instance method

curl -X POST http://localhost:8080/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "methodId": "au.clef.model.AcmeService#personName(au.clef.model.Person)",
    "targetId": "acmeService",
    "args": [
      {
        "type": "object",
        "value": {
          "type": "au.clef.model.Person",
          "fields": {
            "name": { "type": "primitive", "value": "Alice" },
            "age": { "type": "primitive", "value": "25" }
          }
        }
      }
    ]
  }'

Invoke static method

curl -X POST http://localhost:8080/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "methodId": "java.lang.Math#max(int,int)",
    "args": [
      { "type": "primitive", "value": "10" },
      { "type": "primitive", "value": "20" }
    ]
  }'

 */
class WebServer {

    fun start(): Unit {
        val metadata = MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")

        val engine: ReflectionEngine = ReflectionEngine(
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )

        val instanceRegistry: InstanceRegistry = InstanceRegistry(
            mapOf("acmeService" to AcmeService())
        )

        val valueMapper: ValueMapper = ValueMapper(instanceRegistry)
        val api: ReflectionServiceApi = ReflectionServiceApi(engine, instanceRegistry, valueMapper)

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