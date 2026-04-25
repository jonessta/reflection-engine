package au.clef.api.model

import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(
    val executionId: String,
    val args: List<ValueDto> = emptyList()
)