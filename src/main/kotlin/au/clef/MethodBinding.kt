package au.clef

import java.lang.reflect.Method

data class MethodBinding(
    val descriptor: MethodDescriptor,
    val method: Method
)