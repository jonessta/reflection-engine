package au.clef.engine

import java.lang.reflect.Array
import java.lang.reflect.Field
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
            is GenericArrayType -> {
                val componentType: Class<*> = rawClassOf(type.genericComponentType)
                Array.newInstance(componentType, 0).javaClass
            }

            is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull() ?: Any::class.java)
            else -> throw IllegalArgumentException("Unsupported Type: $type")
        }

    fun findField(target: Class<*>, name: String): Field? =
        generateSequence(target) { current: Class<*> -> current.superclass }
            .takeWhile { current: Class<*> -> current != Any::class.java }
            .mapNotNull { current: Class<*> ->
                runCatching { current.getDeclaredField(name) }.getOrNull()
            }
            .firstOrNull()
}