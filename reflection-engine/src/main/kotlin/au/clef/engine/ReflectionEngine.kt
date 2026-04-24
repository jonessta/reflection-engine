package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.engine.registry.ReflectionTypes
import au.clef.metadata.DescriptorMetadataRegistry
import kotlin.reflect.KClass

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val reflectionRegistry: ReflectionRegistry,
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    val reflectionTypes: ReflectionTypes get() = reflectionRegistry

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> {
        val descriptors: List<MethodDescriptor> = reflectionRegistry.descriptors(clazz)
        return metadataRegistry?.applyAll(descriptors) ?: descriptors
    }

    /**
     * No meta data decoration.
     */
    private fun rawDescriptor(id: MethodId): MethodDescriptor =
        reflectionRegistry.descriptor(id)

    /**
     * Decorates the descriptor with metadata.
     */
    fun descriptor(id: MethodId): MethodDescriptor {
        val descriptor: MethodDescriptor = rawDescriptor(id)
        return metadataRegistry?.apply(descriptor) ?: descriptor
    }

    fun invoke(methodId: MethodId, instance: Any, args: List<Value>): Any? =
        invokeDescriptor(descriptor(methodId), instance, args)

    fun invoke(methodId: MethodId, args: List<Value>): Any? =
        invokeDescriptor(descriptor(methodId), null, args)

    fun invoke(methodId: MethodId, instance: Any, vararg args: Value): Any? =
        invokeDescriptor(descriptor(methodId), instance, args.toList())

    fun invoke(methodId: MethodId, vararg args: Value): Any? =
        invokeDescriptor(descriptor(methodId), null, args.toList())

    fun invokeStatic(descriptor: MethodDescriptor, args: List<Value>): Any? =
        invokeDescriptor(descriptor, null, args)

    fun invokeInstance(descriptor: MethodDescriptor, instance: Any, args: List<Value>): Any? =
        invokeDescriptor(descriptor, instance, args)

    private fun invokeDescriptor(
        descriptor: MethodDescriptor,
        instance: Any?,
        args: List<Value>
    ): Any? {
        val method = reflectionRegistry.method(descriptor.id)

        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException("${descriptor.id}")
        }

        require(args.size == method.parameterCount) {
            "Expected ${method.parameterCount} args for ${descriptor.id}, got ${args.size}"
        }

        val convertedArgs = args.mapIndexed { index, arg ->
            typeConverter.materialize(arg, method.parameterTypes[index])
        }.toTypedArray()

        val target = if (descriptor.isStatic) null else instance
        return method.invoke(target, *convertedArgs)
    }
}