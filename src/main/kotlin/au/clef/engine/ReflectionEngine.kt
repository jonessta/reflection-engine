package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val methodRegistry: MethodRegistry,
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> {
        val descriptors: List<MethodDescriptor> = methodRegistry.descriptors(clazz)
        return metadataRegistry?.applyAll(descriptors) ?: descriptors
    }

    fun findDescriptorExact(id: MethodId): MethodDescriptor {
        val descriptor: MethodDescriptor = methodRegistry.findDescriptorById(id)
        return metadataRegistry?.apply(descriptor) ?: descriptor
    }

    fun invoke(methodId: MethodId, instance: Any? = null, args: List<Value>): Any? {
        val descriptor: MethodDescriptor = findDescriptorExact(methodId)
        return invokeDescriptor(descriptor, instance, args)
    }

    fun invoke(methodId: MethodId, instance: Any? = null, vararg args: Value): Any? =
        invoke(methodId, instance, args.toList())

    fun invokeDescriptor(descriptor: MethodDescriptor, instance: Any?, args: List<Value>): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.reflectedName)
        }
        require(args.size == descriptor.method.parameterCount) {
            "Expected ${descriptor.method.parameterCount} args for ${descriptor.id}, got ${args.size}"
        }
        val convertedArgs = args.mapIndexed { i, arg ->
            val paramType = descriptor.method.parameterTypes[i]
            typeConverter.materialize(arg, paramType)
        }
        val target = if (descriptor.isStatic) null else instance
        return descriptor.method.invoke(target, *convertedArgs.toTypedArray())
    }
}