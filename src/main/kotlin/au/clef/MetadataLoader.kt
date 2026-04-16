package au.clef

import kotlinx.serialization.json.Json
import java.io.InputStream

object MetadataLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun fromResourceOrEmpty(path: String): MetadataRoot {
        val stream: InputStream = MetadataLoader::class.java.getResourceAsStream(path)
            ?: return MetadataRoot()

        val text: String = stream.bufferedReader().use { it.readText() }
        if (text.isBlank()) {
            return MetadataRoot()
        }

        return json.decodeFromString(text)
    }
}