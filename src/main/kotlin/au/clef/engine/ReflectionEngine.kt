package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.engine.registry.RegisteredClasses
import au.clef.metadata.DescriptorMetadataRegistry
import kotlin.reflect.KClass

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val reflectionRegistry: ReflectionRegistry,
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) : RegisteredClasses {

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> {
        val descriptors: List<MethodDescriptor> = reflectionRegistry.descriptors(clazz)
        return metadataRegistry?.applyAll(descriptors) ?: descriptors
    }

    /**
     * No meta data decoration.
     */
    fun rawDescriptor(id: MethodId): MethodDescriptor =
        reflectionRegistry.findDescriptorById(id)

    /**
     * Decorates the descriptor with metadata.
     */
    fun descriptor(id: MethodId): MethodDescriptor {
        val descriptor = rawDescriptor(id)
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

    private fun invokeDescriptor(descriptor: MethodDescriptor, instance: Any?, args: List<Value>): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException("${descriptor.id}")
        }
        if (descriptor.isStatic && instance != null) {
            // todo better exception
            throw IllegalArgumentException("Static method ${descriptor.id} must not be invoked with an instance")
        }

        require(args.size == descriptor.method.parameterCount) {
            "Expected ${descriptor.method.parameterCount} args for ${descriptor.id}, got ${args.size}"
        }

        val convertedArgs: List<Any?> = args.mapIndexed { index: Int, arg: Value ->
            val paramType: Class<*> = descriptor.method.parameterTypes[index]
            typeConverter.materialize(arg, paramType)
        }

        val target: Any? = if (descriptor.isStatic) null else instance

        return descriptor.method.invoke(target, *convertedArgs.toTypedArray())
    }

    override val classes: List<Class<*>> get() = reflectionRegistry.classes
}