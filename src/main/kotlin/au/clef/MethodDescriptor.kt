package au.clef

data class MethodDescriptor(
    val name: String,
    val parameters: List<ParamDescriptor>,
    val returnType: Class<*>,
    val isStatic: Boolean,
    val rawMethod: java.lang.reflect.Method
) {
    override fun toString(): String {
        return "MethodDescriptor(name='$name', parameters=$parameters, returnType=$returnType, isStatic=$isStatic, rawMethod=$rawMethod)"
    }
}