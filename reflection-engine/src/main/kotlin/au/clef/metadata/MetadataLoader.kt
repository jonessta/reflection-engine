package au.clef.metadata

import au.clef.metadata.model.MetadataRoot
import kotlinx.serialization.json.Json
import java.io.InputStream

object MetadataLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun fromResourceOrEmpty(path: String): MetadataRoot {
        val stream: InputStream = MetadataLoader::class.java.getResourceAsStream(path)
            ?: error("Metadata resource not found on classpath: $path")

        val text: String = stream.bufferedReader().use { it.readText() }
        println(text)
        if (text.isBlank()) {
            return MetadataRoot()
        }
        return json.decodeFromString(text)
    }
}