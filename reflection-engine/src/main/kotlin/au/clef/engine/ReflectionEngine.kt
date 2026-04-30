package au.clef.engine

import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.engine.registry.MethodSourceTypes
import au.clef.metadata.DescriptorMetadataRegistry
import kotlin.reflect.KClass

class ReflectionEngine(
    reflectionConfig: ReflectionConfig,
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) : MethodSourceTypes {

    private val reflectionRegistry = MethodSourceRegistry(
        methodSources = reflectionConfig.methodSources,
        methodSupportingTypes = reflectionConfig.methodSupportingTypes,
        inheritanceLevel = reflectionConfig.inheritanceLevel
    )

    override val declaringClasses: List<Class<*>>
        get() = reflectionRegistry.declaringClasses

    override val knownClasses: List<Class<*>>
        get() = reflectionRegistry.knownClasses

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        reflectionRegistry.executionContext(executionId)

    fun executionContexts(): List<ExecutionContext> =
        reflectionRegistry.allExecutionContexts()

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> =
        descriptors(clazz.java)

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        decorate(reflectionRegistry.descriptors(clazz))

    fun descriptor(methodId: MethodId): MethodDescriptor =
        decorate(reflectionRegistry.descriptor(methodId))

    fun invokeStatic(methodId: MethodId, vararg args: Any?): Any? =
        invokeStatic(methodId, args.toList())

    fun invokeStatic(methodId: MethodId, args: List<Any?>): Any? =
        invokeDescriptor(descriptor(methodId), null, args)

    fun invokeInstance(methodId: MethodId, instance: Any, vararg args: Any?): Any? =
        invokeInstance(methodId, instance, args.toList())

    fun invokeInstance(methodId: MethodId, instance: Any, args: List<Any?>): Any? =
        invokeDescriptor(descriptor(methodId), instance, args)

    fun invokeStatic(descriptor: MethodDescriptor, args: List<Any?>): Any? =
        invokeDescriptor(descriptor, null, args)

    fun invokeInstance(descriptor: MethodDescriptor, instance: Any, args: List<Any?>): Any? =
        invokeDescriptor(descriptor, instance, args)

    private fun invokeDescriptor(
        descriptor: MethodDescriptor,
        instance: Any?,
        args: List<Any?>
    ): Any? {
        val method = reflectionRegistry.method(descriptor.id)

        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException("${descriptor.id}")
        }

        if (descriptor.isStatic && instance != null) {
            throw IllegalArgumentException(
                "Static method ${descriptor.id} must not receive an instance"
            )
        }

        require(args.size == method.parameterCount) {
            "Expected ${method.parameterCount} args for ${descriptor.id}, got ${args.size}"
        }

        val target = if (descriptor.isStatic) null else instance
        return method.invoke(target, *args.toTypedArray())
    }

    private fun decorate(descriptor: MethodDescriptor): MethodDescriptor =
        metadataRegistry?.applyAll(listOf(descriptor))?.single() ?: descriptor

    private fun decorate(descriptors: List<MethodDescriptor>): List<MethodDescriptor> =
        metadataRegistry?.applyAll(descriptors) ?: descriptors
}