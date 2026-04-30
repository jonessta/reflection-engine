package au.clef.metadata

import au.clef.metadata.model.MetadataRoot
import kotlinx.serialization.json.Json
import java.io.File

object MetadataWriter {

    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(metadata: MetadataRoot): String =
        json.encodeToString(metadata)

    fun writeToFile(metadata: MetadataRoot, file: File): Unit {
        val text: String = toJson(metadata)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}