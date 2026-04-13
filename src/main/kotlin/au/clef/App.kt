package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class App {

    // --------------------------------------------------
    // PUBLIC ENTRY POINTS
    // --------------------------------------------------

    fun invokeWithValues(
        descriptor: MethodDescriptor,
        ctx: ExecutionContext,
        values: List<Any?>
    ): Any? {
        return descriptor.invoke(ctx, values)
    }

    // --------------------------------------------------
    // METHOD DISCOVERY
    // --------------------------------------------------

    fun collectMethods(
        clazz: Class<*>,
        level: InheritanceLevel
    ): List<Method> {

        val result = mutableListOf<Method>()

        var current: Class<*>? = clazz
        var depth = 0

        val maxDepth = when (level) {
            is InheritanceLevel.DeclaredOnly -> 0
            is InheritanceLevel.All -> Int.MAX_VALUE
            is InheritanceLevel.Depth -> level.value
        }

        while (current != null && depth <= maxDepth) {
            result += current.declaredMethods
            current = current.superclass
            depth++
        }

        return result
            .filter {
                Modifier.isPublic(it.modifiers) &&
                        !it.isSynthetic &&
                        !it.isBridge
            }
            .distinctBy {
                it.name + it.parameterTypes.joinToString { t -> t.name }
            }
    }

    fun buildMethods(methods: List<Method>): List<MethodDescriptor> {

        return methods.map { method ->

            val isStatic = Modifier.isStatic(method.modifiers)

            val params = method.parameters.mapIndexed { i, p ->
                ParamDescriptor(
                    index = i,
                    name = p.name ?: "arg$i",
                    type = p.type,
                    nullable = true
                )
            }

            val raw = method

            MethodDescriptor(
                name = method.name,
                parameters = params,
                returnType = method.returnType,
                isStatic = isStatic,
                rawMethod = raw,

                invoke = { ctx, args ->

                    val convertedArgs = args.mapIndexed { i, arg ->
                        materialize(arg, params[i].type)
                    }

                    val target = when {
                        isStatic -> null
                        ctx.instance != null -> ctx.instance
                        else -> throw IllegalStateException("Instance required for ${method.name}")
                    }

                    raw.invoke(target, *convertedArgs.toTypedArray())
                }
            )
        }
    }

    // --------------------------------------------------
    // VALUE MATERIALIZATION
    // --------------------------------------------------

    fun materialize(value: Any?, targetType: Class<*>): Any? {

        val unwrapped = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> value
            else -> value
        }

        if (unwrapped == null) return null

        // 🔥 Object construction path
        if (unwrapped is Value.Object) {
            return buildObject(unwrapped)
        }

        return when (targetType) {

            Int::class.javaPrimitiveType,
            Int::class.java -> when (unwrapped) {
                is Int -> unwrapped
                is String -> unwrapped.toInt()
                else -> error("Cannot convert $value to Int")
            }

            Double::class.javaPrimitiveType,
            Double::class.java -> when (unwrapped) {
                is Double -> unwrapped
                is String -> unwrapped.toDouble()
                else -> error("Cannot convert $value to Double")
            }

            String::class.java -> unwrapped.toString()

            else -> unwrapped
        }
    }

    // --------------------------------------------------
    // OBJECT CONSTRUCTION (HYBRID: KOTLIN + JAVA)
    // --------------------------------------------------

    fun buildObject(obj: Value.Object): Any {
        val clazz = obj.type

        return if (isKotlinClass(clazz)) {
            buildKotlinObject(obj)
        } else {
            buildJavaObject(obj)
        }
    }

    // ✅ Kotlin reflection (clean, reliable)
    private fun buildKotlinObject(obj: Value.Object): Any {
        val kClass: KClass<*> = obj.type.kotlin

        val ctor = kClass.primaryConstructor
            ?: throw IllegalArgumentException("No primary constructor for ${obj.type.name}")

        val argsByParam = mutableMapOf<KParameter, Any?>()

        for (param in ctor.parameters) {

            val name = param.name
                ?: throw IllegalArgumentException("Unnamed constructor parameter in ${obj.type.name}")

            val rawValue = obj.fields[name]

            if (rawValue == null) {
                if (param.isOptional) continue
                if (param.type.isMarkedNullable) {
                    argsByParam[param] = null
                    continue
                }
                throw IllegalArgumentException("Missing field '$name' for ${obj.type.name}")
            }

            val paramClass = (param.type.classifier as? KClass<*>)?.java
                ?: throw IllegalArgumentException("Unsupported type for '$name' in ${obj.type.name}")

            argsByParam[param] = materialize(rawValue, paramClass)
        }

        return ctor.callBy(argsByParam)
    }

    // ✅ Java reflection fallback
    private fun buildJavaObject(obj: Value.Object): Any {
        val clazz = obj.type

        for (ctor in clazz.declaredConstructors) {

            val params = ctor.parameters

            if (params.size != obj.fields.size) continue

            try {
                val args = params.mapIndexed { i, param ->
                    val value =
                        obj.fields[param.name]
                            ?: obj.fields["arg$i"]
                            ?: obj.fields.values.elementAtOrNull(i)
                            ?: throw IllegalArgumentException("Missing value for param $i")

                    materialize(value, param.type)
                }.toTypedArray()

                ctor.isAccessible = true
                return ctor.newInstance(*args)

            } catch (_: Exception) {
            }
        }

        val noArgCtor = clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw IllegalArgumentException(
                "Could not construct ${clazz.name}: no suitable constructor"
            )

        noArgCtor.isAccessible = true
        val instance = noArgCtor.newInstance()

        obj.fields.forEach { (name, value) ->
            runCatching {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                field.set(instance, materialize(value, field.type))
            }
        }

        return instance
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private fun isKotlinClass(clazz: Class<*>): Boolean =
        clazz.getAnnotation(Metadata::class.java) != null
}