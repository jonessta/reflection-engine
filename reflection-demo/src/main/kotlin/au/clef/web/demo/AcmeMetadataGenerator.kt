package au.clef.web.demo

import au.clef.metadata.MetadataGenerationConfig
import au.clef.metadata.generateMetadata
import java.io.File

fun main() {
    val metadataConfig = MetadataGenerationConfig(acmeConfig, outputFile = File("reflection-demo/src/main/resources"))
    generateMetadata(metadataConfig)
}

