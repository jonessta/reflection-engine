package au.clef.engine

import au.clef.engine.model.MethodBinding
import au.clef.engine.model.MethodDescriptor
import au.clef.MethodNotFoundException
import au.clef.MissingInstanceException
import au.clef.engine.convert.TypeConverter
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val methodRegistry: MethodRegistry = MethodRegistry(),
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> {
        val bindings: List<MethodBinding> = methodRegistry.bindings(clazz)
        val descriptors: List<MethodDescriptor> = bindings.map { it.descriptor }

        return metadataRegistry?.applyAll(descriptors) ?: descriptors
    }

    fun findBindingById(clazz: Class<*>, id: String): MethodBinding {
        val bindings: List<MethodBinding> = methodRegistry.bindings(clazz)

        return bindings.firstOrNull { it.descriptor.id == id }
            ?: throw MethodNotFoundException(
                owner = clazz,
                methodName = id,
                parameterTypes = emptyList(),
                staticOnly = null,
                availableOverloads = bindings.map { it.descriptor.id })
    }

    fun findDescriptorExact(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>
    ): MethodDescriptor {
        val methods: List<MethodDescriptor> = descriptors(clazz)
        return methods.firstOrNull {
            it.name == methodName && methodKey(it) == "$methodName(${parameterTypes.joinToString(",") { it.name }})"
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = null,
            availableOverloads = methods.filter { it.name == methodName }.map { it.id.substringAfter("#") })
    }

    private fun methodKey(descriptor: MethodDescriptor): String =
        descriptor.id.substringAfter("#")

    fun invokeBinding(
        binding: MethodBinding,
        instance: Any? = null,
        args: List<Any?>
    ): Any? {
        val descriptor: MethodDescriptor = binding.descriptor

        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.name)
        }

        val convertedArgs: List<Any?> = args.mapIndexed { i, arg ->
            val paramType: Class<*> = binding.method.parameterTypes[i]
            typeConverter.materialize(arg, paramType)
        }

        val target = if (descriptor.isStatic) null else instance

        return binding.method.invoke(target, *convertedArgs.toTypedArray())
    }
}