package au.clef.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class InvocationResponse(
    val result: JsonElement? = null
)