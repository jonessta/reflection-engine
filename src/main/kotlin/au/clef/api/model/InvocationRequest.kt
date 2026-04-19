package au.clef.api.model

import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(
    val methodId: String,
    val targetId: String? = null,
    val args: List<ValueDto> = emptyList()
)