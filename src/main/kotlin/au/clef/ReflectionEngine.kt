package au.clef

import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter(),
    private val methodRegistry: MethodRegistry = MethodRegistry(),
    private val metadataRegistry: DescriptorMetadataRegistry? = null
) {

    fun descriptors(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<MethodDescriptor> {
        val base: List<MethodDescriptor> = methodRegistry.descriptors(clazz, inheritanceLevel)
        return metadataRegistry?.applyAll(base) ?: base
    }

    fun descriptor(function: KFunction<*>): MethodDescriptor {
        val method: Method =
            function.javaMethod ?: throw EngineException("Function '${function.name}' is not backed by a Java method")

        return methodRegistry.descriptors(method.declaringClass).first { it.method == method }
    }

    fun findDescriptorExact(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MethodDescriptor = methodRegistry.findDescriptorExact(clazz, methodName, parameterTypes, inheritanceLevel)

    fun invokeDescriptor(descriptor: MethodDescriptor, instance: Any? = null, args: List<Any?>): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.name)
        }

        val convertedArgs: List<Any?> = args.mapIndexed { i: Int, arg: Any? ->
            typeConverter.materialize(arg, descriptor.parameters[i].type)
        }

        val target: Any? = if (descriptor.isStatic) null else instance

        return descriptor.method.invoke(target, *convertedArgs.toTypedArray())
    }
}