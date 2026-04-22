package au.clef.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class ValueDto {

    @Serializable
    @SerialName("scalar")
    data class Scalar(val value: JsonElement) : ValueDto()

    @Serializable
    @SerialName("instance")
    data class InstanceRef(val id: String) : ValueDto()

    @Serializable
    @SerialName("object")
    data class Record(
        val type: String,
        val fields: Map<String, ValueDto>
    ) : ValueDto()

    @Serializable
    @SerialName("list")
    data class ListValue(val items: List<ValueDto>) : ValueDto()

    @Serializable
    @SerialName("map")
    data class MapValue(val entries: Map<String, ValueDto>) : ValueDto()

    @Serializable
    @SerialName("null")
    data object Null : ValueDto()
}