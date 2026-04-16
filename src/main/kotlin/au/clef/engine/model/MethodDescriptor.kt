package au.clef.engine.model

data class MethodDescriptor(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>,
    val returnType: String,
    val isStatic: Boolean
)

data class ParamDescriptor(
    val index: Int,
    val name: String,
    val label: String? = null,
    val type: String,
    val nullable: Boolean
)