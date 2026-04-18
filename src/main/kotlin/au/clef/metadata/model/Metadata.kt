package au.clef.metadata.model

import au.clef.engine.model.MethodId
import kotlinx.serialization.Serializable

@Serializable
data class MetadataRoot(
    val methods: Map<MethodId, MethodMetadata> = emptyMap()
)

@Serializable
data class MethodMetadata(
    val displayName: String? = null,
    val hidden: Boolean = false,
    val order: Int? = null,
    val parameters: List<ParamMetadata> = emptyList()
)

@Serializable
data class ParamMetadata(
    val name: String? = null,
    val label: String? = null
)