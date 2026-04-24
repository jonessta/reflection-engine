package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.engine.ExposedTarget
import java.io.File
import kotlin.reflect.KClass

object AcmeDemoConfig {
    const val METADATA_RESOURCE_PATH = "/config/acme-metadata.json"

    val targets = listOf(
        ExposedTarget.Instance("acmeService", AcmeService())
    )

    val targetSupportingTypes: List<KClass<*>> = listOf(
        Person::class,
        Address::class
    )

    val metadataOutputFile: File =
        File("reflection-demo/src/main/resources")
            .resolve(METADATA_RESOURCE_PATH.removePrefix("/"))
}