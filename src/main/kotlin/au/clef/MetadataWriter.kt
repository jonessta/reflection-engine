package au.clef

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object MetadataWriter {

    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(metadata: MetadataRoot): String = json.encodeToString(metadata)

    fun writeToFile(metadata: MetadataRoot, file: File) {
        val text: String = toJson(metadata)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}