package au.clef.api.model

import au.clef.engine.model.ExecutionId
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionDescriptorDto(
    val executionId: ExecutionId,
    val sourceDescription: String? = null,
    val reflectedName: String,
    val displayName: String? = null,
    val returnType: String,
    val isStatic: Boolean,
    val parameters: List<FieldDescriptorDto>
)

