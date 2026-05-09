package au.clef.api

import au.clef.engine.registry.MethodSourceRegistry
import java.lang.reflect.Method
import java.lang.reflect.Type

class MethodTypeRoots(
    private val methodSourceRegistry: MethodSourceRegistry
) {
    // todo why have this class at all why not use MethodSourceRegistry directly?
    fun all(): List<Type> =
        methodSourceRegistry.exposedMethods().flatMap { method: Method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
}