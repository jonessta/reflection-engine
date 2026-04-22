package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.engine.ReflectionEngine
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader

fun main() {
    val methodRegistry = MethodRegistry(
        AcmeService::class,
        Math::class,
        Person::class,
        Address::class
    )

    val metadata = MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")

    val engine = ReflectionEngine(
        methodRegistry = methodRegistry,
        metadataRegistry = DescriptorMetadataRegistry(metadata)
    )

    val instanceRegistry = InstanceRegistry(
        mapOf("acmeService" to AcmeService())
    )

    val api = ReflectionServiceApi(
        engine = engine,
        instanceRegistry = instanceRegistry
    )

    WebServer(api, port = 8080).start()
}