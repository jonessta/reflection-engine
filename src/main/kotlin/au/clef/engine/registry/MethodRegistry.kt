package au.clef.engine.registry

import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MethodRegistry {

    private val descriptorsByClass: MutableMap<Class<*>, List<MethodDescriptor>> = ConcurrentHashMap()
    private val descriptorsById: MutableMap<MethodId, MethodDescriptor> = ConcurrentHashMap()

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> {
        return descriptorsByClass.getOrPut(clazz) {
            val descriptors: List<MethodDescriptor> = buildDescriptors(clazz)
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

        val clazz: Class<*> = id.declaringClass
        val descriptors: List<MethodDescriptor> = descriptors(clazz)

        return descriptorsById[id] ?: throw MethodNotFoundException(
            owner = clazz,
            methodId = id,
            available = descriptors.map { descriptor: MethodDescriptor -> descriptor.id.toString() }
        )
    }

    private fun buildDescriptors(clazz: Class<*>): List<MethodDescriptor> {
        val methods: Array<Method> = clazz.declaredMethods

        return methods.map { method: Method ->
            MethodDescriptor(
                id = MethodId.fromMethod(method),
                method = method,
                displayName = null,
                parameters = buildParamDescriptors(method)
            )
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
        descriptorsByClass.clear()
        descriptorsById.clear()
    }
}