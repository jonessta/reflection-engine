package au.clef.engine

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

object TypeReflection {

    fun rawClassOf(type: Type): Class<*> =
        when (type) {
            is Class<*> -> type
            is ParameterizedType -> rawClassOf(type.rawType)
            is WildcardType -> rawClassOf(type.upperBounds.firstOrNull() ?: Any::class.java)
            is GenericArrayType ->
                java.lang.reflect.Array.newInstance(
                    rawClassOf(type.genericComponentType),
                    0
                ).javaClass

            is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull() ?: Any::class.java)
            else -> throw IllegalArgumentException("Unsupported Type: $type")
        }
}