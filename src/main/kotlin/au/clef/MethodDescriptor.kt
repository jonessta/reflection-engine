package au.clef

data class MethodDescriptor(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>,
    val returnType: String,
    val isStatic: Boolean
)