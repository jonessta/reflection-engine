package au.clef.engine.model

import java.lang.reflect.Method

data class MethodBinding(
    val descriptor: MethodDescriptor,
    val method: Method
)