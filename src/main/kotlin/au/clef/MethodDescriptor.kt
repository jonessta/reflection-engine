package au.clef

import java.lang.reflect.Method

class MethodDescriptor(
    val name: String,
    val parameters: List<ParamDescriptor>,
    val returnType: Class<*>,
    val isStatic: Boolean,
    val rawMethod: java.lang.reflect.Method,
    val invoke: (ExecutionContext, List<Any?>) -> Any?
) {
    override fun toString(): String {
        return "MethodDescriptor(name='$name', parameters=$parameters, returnType=$returnType, isStatic=$isStatic)"
    }
}