package au.clef.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
sealed class ValueDto {

    @Serializable
    @SerialName("scalar")
    data class Scalar(val value: JsonPrimitive?) : ValueDto()

    @Serializable
    @SerialName("record")
    data class Record(
        val type: String,
        val fields: Map<String, ValueDto>
    ) : ValueDto()

    @Serializable
    @SerialName("list")
    data class ListValue(
        val items: List<ValueDto>
    ) : ValueDto()

    @Serializable
    @SerialName("map")
    data class MapValue(
        val entries: List<MapEntryDto>
    ) : ValueDto()

    @Serializable
    @SerialName("null")
    data object Null : ValueDto()
}

@Serializable
data class MapEntryDto(
    val key: ValueDto,
    val value: ValueDto
)