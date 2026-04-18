package au.clef.engine.registry

import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MethodRegistry {

    private val cache: MutableMap<Class<*>, List<MethodDescriptor>> = ConcurrentHashMap()

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        cache.getOrPut(clazz) { buildDescriptors(clazz) }

    fun findDescriptorById(clazz: Class<*>, id: MethodId): MethodDescriptor {
        val descriptors: List<MethodDescriptor> = descriptors(clazz)

        return descriptors.firstOrNull { descriptor: MethodDescriptor ->
            descriptor.id == id
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodId = id,
            available = descriptors.map { descriptor: MethodDescriptor -> descriptor.id.toString() }
        )
    }

    fun findDescriptorById(id: MethodId): MethodDescriptor {
        return findDescriptorById(id.clazz, id)
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
                rawName = "arg$index",
                name = "arg$index",
                label = null,
                nullable = !type.isPrimitive
            )
        }
    }
}