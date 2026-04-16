package au.clef.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ValueDto {

    @Serializable
    @SerialName("primitive")
    data class Primitive(
        val value: String? = null
    ) : ValueDto()

    @Serializable
    @SerialName("instance")
    data class Instance(
        val id: String
    ) : ValueDto()

    @Serializable
    @SerialName("object")
    data class Object(
        val type: String,
        val fields: Map<String, ValueDto>
    ) : ValueDto()
}