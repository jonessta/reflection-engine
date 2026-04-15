package au.clef

import kotlinx.serialization.Serializable

@Serializable
data class MetadataRoot(
    val classes: Map<String, ClassMetadata> = emptyMap()
)

@Serializable
data class ClassMetadata(
    val methods: Map<String, MethodMetadata> = emptyMap()
)

@Serializable
data class MethodMetadata(
    val displayName: String? = null,
    val parameters: List<ParamMetadata> = emptyList()
)

@Serializable
data class ParamMetadata(
    val name: String? = null,
    val label: String? = null
)