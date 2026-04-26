package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.engine.registry.ReflectionTypes
import au.clef.metadata.DescriptorMetadataRegistry
import java.lang.reflect.Method
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
     * Returns the descriptor without metadata decoration.
     */
    private fun rawDescriptor(id: MethodId): MethodDescriptor = reflectionRegistry.descriptor(id)

    /**
     * Returns the descriptor with metadata decoration applied when available.
     */
    fun descriptor(id: MethodId): MethodDescriptor {
        val descriptor: MethodDescriptor = rawDescriptor(id)
        return metadataRegistry?.apply(descriptor) ?: descriptor
    }

    fun invokeInstance(methodId: MethodId, instance: Any, args: List<Value>): Any? =
        invokeDescriptor(descriptor(methodId), instance, args)

    fun invokeStatic(methodId: MethodId, args: List<Value>): Any? =
        invokeDescriptor(descriptor(methodId), null, args)

    fun invokeInstance(methodId: MethodId, instance: Any, vararg args: Value): Any? =
        invokeDescriptor(descriptor(methodId), instance, args.toList())

    fun invokeStatic(methodId: MethodId, vararg args: Value): Any? =
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
        val method: Method = reflectionRegistry.method(descriptor.id)

        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException("${descriptor.id}")
        }

        if (descriptor.isStatic && instance != null) {
            throw IllegalArgumentException(
                "Static method ${descriptor.id} must not be invoked with an instance"
            )
        }

        require(args.size == method.parameterCount) {
            "Expected ${method.parameterCount} args for ${descriptor.id}, got ${args.size}"
        }

        val convertedArgs: Array<Any?> = args.mapIndexed { index, arg ->
            typeConverter.materialize(arg, method.parameterTypes[index])
        }.toTypedArray()

        val targetObject = if (descriptor.isStatic) null else instance
        return method.invoke(targetObject, *convertedArgs)
    }
}