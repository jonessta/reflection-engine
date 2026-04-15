package au.clef

import kotlinx.serialization.json.Json

object MetadataLoader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun fromResourceOrEmpty(path: String): MetadataRoot {
        val stream = MetadataLoader::class.java.getResourceAsStream(path)
            ?: return MetadataRoot()

        return json.decodeFromString(stream.readBytes().decodeToString())
    }
}