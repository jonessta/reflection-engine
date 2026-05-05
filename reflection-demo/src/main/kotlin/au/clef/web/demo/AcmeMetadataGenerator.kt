package au.clef.web.demo

import au.clef.metadata.MetadataGenerationConfig
import au.clef.metadata.generateMetadata
import java.io.File

private val metadataOutputFile = File("reflection-demo/src/main/resources")
    .resolve(requireNotNull(acmeConfig.metadataResourcePath) {
        "metadataResourcePath must be configured for metadata generation"
    }.removePrefix("/"))

fun main() {
    val metadataConfig = MetadataGenerationConfig(acmeConfig, outputFile = metadataOutputFile)
    generateMetadata(metadataConfig)
}

