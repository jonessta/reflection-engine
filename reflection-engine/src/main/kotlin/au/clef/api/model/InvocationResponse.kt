package au.clef.api.model

import kotlinx.serialization.Serializable

@Serializable
data class InvocationResponse(
    val result: ValueDto
)