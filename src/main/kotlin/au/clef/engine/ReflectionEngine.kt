package au.clef.engine

import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.*
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val methodRegistry: MethodRegistry = MethodRegistry(),
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    fun descriptors(
        clazz: Class<*>, inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> {
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

    fun invokeDescriptor(
        descriptor: MethodDescriptor, instance: Any? = null, args: List<Any?>
    ): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.reflectedName)
        }
        val convertedArgs: List<Any?> = args.mapIndexed { index: Int, arg: Any? ->
            val paramType: Class<*> = descriptor.method.parameterTypes[index]
            typeConverter.materialize(arg, paramType)
        }
        val target: Any? = if (descriptor.isStatic) null else instance
        return descriptor.method.invoke(target, *convertedArgs.toTypedArray())
    }
}