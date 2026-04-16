package au.clef

data class ParamDescriptor(
    val index: Int,
    val name: String,
    val label: String? = null,
    val type: Class<*>,
    val nullable: Boolean
) {
    override fun toString(): String {
        return "ParamDescriptor(index=$index, name='$name', label=$label, type=$type, nullable=$nullable)"
    }
}