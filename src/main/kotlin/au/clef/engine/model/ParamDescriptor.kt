package au.clef.engine.model

data class ParamDescriptor(
    val index: Int,
    val name: String,
    val label: String? = null,
    val type: String,
    val nullable: Boolean
)