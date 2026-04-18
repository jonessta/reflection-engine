package au.clef.engine.registry

import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MethodRegistry {

    private data class DescriptorCacheKey(
        val clazz: Class<*>,
        val inheritanceLevel: InheritanceLevel
    )

    private val descriptorsByQuery: MutableMap<DescriptorCacheKey, List<MethodDescriptor>> = ConcurrentHashMap()
    private val descriptorsById: MutableMap<MethodId, MethodDescriptor> = ConcurrentHashMap()

    fun descriptors(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> {
        val key = DescriptorCacheKey(clazz, inheritanceLevel)
        return descriptorsByQuery.getOrPut(key) {
            val descriptors: List<MethodDescriptor> = buildDescriptors(clazz, inheritanceLevel)
            descriptors.forEach { descriptor: MethodDescriptor ->
                descriptorsById[descriptor.id] = descriptor
            }
            descriptors
        }
    }

    fun findDescriptorById(id: MethodId): MethodDescriptor {
        descriptorsById[id]?.let { descriptor: MethodDescriptor ->
            return descriptor
        }

        val clazz: Class<*> = classFromMethodId(id)

        // todo put InheritanceLevel in function param ?
        descriptors(clazz, InheritanceLevel.All)

        return descriptorsById[id] ?: throw MethodNotFoundException(
            methodId = id,
            available = descriptorsById.keys.map { methodId: MethodId -> methodId.toString() }
        )
    }

    fun allDescriptors(): List<MethodDescriptor> =
        descriptorsById.values.toList()

    private fun buildDescriptors(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel
    ): List<MethodDescriptor> {
        val methods: List<Method> = collectMethods(clazz, inheritanceLevel)

        return methods.map { method: Method ->
            MethodDescriptor(
                id = MethodId.fromMethod(method),
                method = method,
                displayName = null,
                parameters = buildParamDescriptors(method)
            )
        }
    }

    private fun collectMethods(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel
    ): List<Method> {
        return when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly -> {
                clazz.declaredMethods.toList()
            }

            InheritanceLevel.All -> {
                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz

                while (current != null) {
                    methods += current.declaredMethods
                    current = current.superclass
                }

                methods.distinctBy { method: Method ->
                    MethodId.fromMethod(method)
                }
            }

            is InheritanceLevel.Depth -> {
                require(inheritanceLevel.value >= 0) {
                    "Depth must be >= 0"
                }

                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz
                var depth: Int = 0

                while (current != null && depth <= inheritanceLevel.value) {
                    methods += current.declaredMethods
                    current = current.superclass
                    depth++
                }

                methods.distinctBy { method: Method ->
                    MethodId.fromMethod(method)
                }
            }
        }
    }

    private fun buildParamDescriptors(method: Method): List<ParamDescriptor> {
        val parameters: Array<java.lang.reflect.Parameter> = method.parameters
        return parameters.mapIndexed { index: Int, parameter: java.lang.reflect.Parameter ->
            ParamDescriptor(
                index = index,
                type = parameter.type,
                reflectedName = parameter.name ?: "arg$index",
                name = parameter.name ?: "arg$index",
                label = null,
                nullable = !parameter.type.isPrimitive
            )
        }
    }

    private fun classFromMethodId(id: MethodId): Class<*> {
        val className: String = id.value.substringBefore("#")
        return Class.forName(className)
    }

    fun clearCache(): Unit {
        descriptorsByQuery.clear()
        descriptorsById.clear()
    }
}