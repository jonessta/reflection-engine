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
        val key: DescriptorCacheKey = DescriptorCacheKey(clazz, inheritanceLevel)

        return descriptorsByQuery.getOrPut(key) {
            val descriptors: List<MethodDescriptor> = buildDescriptors(clazz, inheritanceLevel)

            descriptors.forEach { descriptor: MethodDescriptor ->
                descriptorsById[descriptor.id] = descriptor
            }

            descriptors
        }
    }

    fun findDescriptorById(id: MethodId): MethodDescriptor {
        return descriptorsById[id] ?: throw MethodNotFoundException(
            methodId = id,
            available = descriptorsById.keys.map { methodId: MethodId -> methodId.toString() }
        )
    }

    fun allDescriptors(): List<MethodDescriptor> {
        return descriptorsById.values.toList()
    }

    private fun buildDescriptors(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<MethodDescriptor> {
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
                // todo remove this this should be checked at construction
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
        val paramTypes: Array<Class<*>> = method.parameterTypes
        return paramTypes.mapIndexed { index: Int, type: Class<*> ->
            ParamDescriptor(
                index = index,
                type = type,
                reflectedName = "arg$index",
                name = "arg$index",
                label = null,
                nullable = !type.isPrimitive
            )
        }
    }

    fun clearCache(): Unit {
        descriptorsByQuery.clear()
        descriptorsById.clear()
    }
}