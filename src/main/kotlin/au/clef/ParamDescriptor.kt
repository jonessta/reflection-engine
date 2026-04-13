package au.clef

data class ParamDescriptor(
    val index: Int,
    val name: String,
    val type: Class<*>,
    val nullable: Boolean
) {
    override fun toString(): String {
        return "ParamDescriptor(index=$index, name='$name', type=$type, nullable=$nullable)"
    }
}