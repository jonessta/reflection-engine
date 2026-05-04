package au.clef.api.model

import au.clef.engine.model.ExecutionId
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(
    @Contextual val executionId: ExecutionId,
    val args: List<@Contextual Value> = emptyList()
)