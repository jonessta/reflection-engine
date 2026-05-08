package au.clef.engine

import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

class KnownTypeDiscoverer(private val terminalTypeDecider: TerminalTypeDecider) {

    fun discover(rootTypes: Collection<Type>): Set<Class<*>> {
        val discovered = linkedSetOf<Class<*>>()
        val queue = ArrayDeque<Type>()

        rootTypes.forEach(queue::addLast)

        while (queue.isNotEmpty()) {
            val next: Type = queue.removeFirst()
            val raw: Class<*> = rawClassOf(next)

            if (terminalTypeDecider.isTerminal(raw)) {
                continue
            }

            if (!discovered.add(raw)) {
                continue
            }

            childTypesOf(raw).forEach(queue::addLast)
        }

        return discovered
    }

    private fun childTypesOf(type: Class<*>): List<Type> {
        val result = mutableListOf<Type>()

        type.declaredFields
            .filter { field: Field -> !Modifier.isStatic(field.modifiers) && !field.isSynthetic }
            .forEach { field: Field -> result += field.genericType }

        type.kotlin.primaryConstructor
            ?.parameters
            ?.filter { parameter: KParameter ->
                parameter.kind == KParameter.Kind.VALUE
            }
            ?.forEach { parameter: KParameter ->
                result += parameter.type.javaType
            }

        return result
    }

    private fun rawClassOf(type: Type): Class<*> =
        when (type) {
            is Class<*> -> type
            is ParameterizedType -> rawClassOf(type.rawType)
            is WildcardType -> rawClassOf(type.upperBounds.firstOrNull() ?: Any::class.java)
            is GenericArrayType -> java.lang.reflect.Array.newInstance(
                rawClassOf(type.genericComponentType),
                0
            ).javaClass

            is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull() ?: Any::class.java)
            else -> throw IllegalArgumentException("Unsupported Type: $type")
        }
}