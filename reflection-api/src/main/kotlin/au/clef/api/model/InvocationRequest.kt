package au.clef.api.model

import au.clef.engine.ExecutionId
import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(
    val executionId: ExecutionId,
    val args: List<ValueDto> = emptyList()
)