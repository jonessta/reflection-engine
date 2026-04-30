package au.clef.metadata

import au.clef.metadata.model.MetadataRoot
import kotlinx.serialization.json.Json
import java.io.InputStream

object MetadataLoader {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun fromResource(path: String): MetadataRoot {
        val stream: InputStream =
            MetadataLoader::class.java.getResourceAsStream(path)
                ?: error("Metadata resource not found on classpath: $path")

        val text: String =
            stream.bufferedReader().use { reader -> reader.readText() }

        if (text.isBlank()) {
            return MetadataRoot()
        }

        return json.decodeFromString(text)
    }
}