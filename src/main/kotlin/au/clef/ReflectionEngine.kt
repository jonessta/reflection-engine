package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class ReflectionEngine(
    private val typeConverter: TypeConverter = TypeConverter()
) {

    fun descriptor(function: KFunction<*>): MethodDescriptor {
        val method: Method =
            function.javaMethod ?: throw EngineException("Function '${function.name}' is not backed by a Java method")
        return buildMethods(listOf(method)).first()
    }

    fun descriptors(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> =
        buildMethods(collectMethods(clazz, inheritanceLevel))

    fun invokeDescriptor(
        descriptor: MethodDescriptor,
        instance: Any? = null,
        args: List<Any?>
    ): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.name)
        }

        val convertedArgs: List<Any?> = args.mapIndexed { i: Int, arg: Any? ->
            typeConverter.materialize(arg, descriptor.parameters[i].type)
        }

        val target: Any? = if (descriptor.isStatic) null else instance
        return descriptor.rawMethod.invoke(target, *convertedArgs.toTypedArray())
    }

    fun findDescriptorExact(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MethodDescriptor {
        val methods: List<MethodDescriptor> = descriptors(clazz, inheritanceLevel)
        return methods.firstOrNull {
            it.name == methodName && it.rawMethod.parameterTypes.toList() == parameterTypes
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = null,
            availableOverloads = methods.filter { it.name == methodName }.map { signature(it) }
        )
    }

    internal fun collectMethods(clazz: Class<*>, level: InheritanceLevel): List<Method> {
        val result: MutableList<Method> = mutableListOf()
        var current: Class<*>? = clazz
        var depth = 0
        val maxDepth: Int = when (level) {
            is InheritanceLevel.DeclaredOnly -> 0
            is InheritanceLevel.All -> Int.MAX_VALUE
            is InheritanceLevel.Depth -> level.value
        }

        while (current != null && depth <= maxDepth) {
            result += current.declaredMethods
            current = current.superclass
            depth++
        }

        return result.filter { method: Method ->
            Modifier.isPublic(method.modifiers) && !method.isSynthetic && !method.isBridge
        }.distinctBy { method: Method ->
            "${method.name}(${method.parameterTypes.joinToString(",") { t: Class<*> -> t.name }})"
        }
    }

    internal fun buildMethods(methods: List<Method>): List<MethodDescriptor> =
        methods.map { method: Method ->
            val isStatic: Boolean = Modifier.isStatic(method.modifiers)
            val params: List<ParamDescriptor> = method.parameters.mapIndexed { i: Int, p ->
                ParamDescriptor(
                    index = i,
                    name = p.name ?: "arg$i",
                    type = p.type,
                    nullable = true
                )
            }

            MethodDescriptor(
                name = method.name,
                parameters = params,
                returnType = method.returnType,
                isStatic = isStatic,
                rawMethod = method
            )
        }

    private fun signature(m: MethodDescriptor): String =
        "${m.name}(${m.parameters.joinToString(", ") { it.type.simpleName }})"
}