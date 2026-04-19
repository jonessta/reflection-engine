package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.*
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import kotlin.reflect.KClass

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val methodRegistry: MethodRegistry = MethodRegistry(),
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    fun descriptors(
        clazz: KClass<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> =
        descriptors(clazz.java, inheritanceLevel)

    fun descriptors(clazz: Class<*>,
                    inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly)
    : List<MethodDescriptor> {
        val descriptors: List<MethodDescriptor> = methodRegistry.descriptors(clazz, inheritanceLevel)
        return metadataRegistry?.applyAll(descriptors) ?: descriptors
    }

    fun findDescriptorExact(id: MethodId): MethodDescriptor {
        val descriptor: MethodDescriptor = methodRegistry.findDescriptorById(id)
        return metadataRegistry?.apply(descriptor) ?: descriptor
    }

    fun findDescriptorExact(
        clazz: Class<*>, methodName: String, parameterTypes: List<Class<*>>
    ): MethodDescriptor {
        val methodId: MethodId = MethodId.from(
            clazz.getMethod(methodName, *parameterTypes.toTypedArray())
        )
        return findDescriptorExact(methodId)
    }

    fun invoke(
        methodId: MethodId, instance: Any? = null, args: List<Value>
    ): Any? {
        val descriptor: MethodDescriptor = findDescriptorExact(methodId)
        return invokeDescriptor(descriptor, instance, args)
    }

    fun invoke(
        methodId: MethodId, instance: Any? = null, vararg args: Value
    ): Any? = invoke(methodId, instance, args.toList())

    fun invoke(
        methodId: MethodId, vararg args: Value
    ): Any? = invoke(methodId, null, args.toList())

    fun invokeDescriptor(
        descriptor: MethodDescriptor, vararg args: Value
    ): Any? = invokeDescriptor(descriptor, null, args.toList())

    fun invokeDescriptor(
        descriptor: MethodDescriptor, instance: Any, vararg args: Value
    ): Any? = invokeDescriptor(descriptor, instance, args.toList())

    fun invokeDescriptor(
        descriptor: MethodDescriptor, instance: Any?, args: List<Value>
    ): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.reflectedName)
        }
        require(args.size == descriptor.method.parameterCount) {
            "Expected ${descriptor.method.parameterCount} arguments for ${descriptor.id}, but got ${args.size}"
        }
        val convertedArgs: List<Any?> = args.mapIndexed { index: Int, arg: Value ->
            val paramType: Class<*> = descriptor.method.parameterTypes[index]
            typeConverter.materialize(arg, paramType)
        }
        val target: Any? = if (descriptor.isStatic) null else instance
        return descriptor.method.invoke(target, *convertedArgs.toTypedArray())
    }
}