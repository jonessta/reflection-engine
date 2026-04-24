package au.clef.web.demo

import au.clef.app.demo.AcmeDemoConfig
import au.clef.metadata.MetadataGenerationConfig
import au.clef.metadata.generateMetadata

fun main() {
    generateMetadata(
        MetadataGenerationConfig(
            targets = AcmeDemoConfig.targets,
            targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
            outputFile = AcmeDemoConfig.metadataOutputFile
        )
    )
}