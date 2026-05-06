package au.clef.api.model

import kotlinx.serialization.Serializable

@Serializable
data class FieldDescriptorDto(
    val index: Int? = null,
    val reflectedName: String,
    val name: String,
    val type: String,
    val nullable: Boolean,
    val kind: FieldKindDto,
    val children: List<FieldDescriptorDto> = emptyList()
)

@Serializable
enum class FieldKindDto {

    SCALAR,
    RECORD
}