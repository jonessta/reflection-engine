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
        return descriptorsById[id] ?: throw MethodNotFoundException(
            methodId = id,
            available = descriptorsById.keys.map { methodId: MethodId -> methodId.toString() }
        )
    }

    /**
     * Returns all descriptors that have been discovered so far.
     * NOTE: Only includes classes that have already been scanned via descriptors(clazz).
     */
    fun allDescriptors(): List<MethodDescriptor> {
        return descriptorsById.values.toList()
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