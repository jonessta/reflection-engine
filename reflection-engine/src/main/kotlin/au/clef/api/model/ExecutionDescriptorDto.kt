package au.clef.api.model

import au.clef.engine.ExecutionId
import kotlinx.serialization.Serializable

/**
 * @param instanceId for UI display only, not required for execution
 */
@Serializable
data class ExecutionDescriptorDto(
    val executionId: ExecutionId,
    val instanceId: String? = null,
    val reflectedName: String,
    val displayName: String? = null,
    val returnType: String,
    val isStatic: Boolean,
    val parameters: List<ParamDescriptorDto>
)

@Serializable
data class ParamDescriptorDto(
    val index: Int,
    val type: String,
    val reflectedName: String,
    val name: String,
    val label: String? = null,
    val nullable: Boolean
)
