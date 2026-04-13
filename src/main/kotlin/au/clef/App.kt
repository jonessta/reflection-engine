package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class App {
    private fun signature(m: MethodDescriptor): String =
        "${m.name}(${m.parameters.joinToString(", ") { it.type.simpleName }})"

    private fun canConvert(value: Any?, targetType: Class<*>): Boolean {
        val unwrapped = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> return true // always constructible
            else -> value
        }

        if (unwrapped == null) return !targetType.isPrimitive

        return when (targetType) {
            Int::class.javaPrimitiveType,
            Int::class.java -> unwrapped is Int || unwrapped is String

            Double::class.javaPrimitiveType,
            Double::class.java -> unwrapped is Double || unwrapped is String

            String::class.java -> true

            else -> targetType.isAssignableFrom(unwrapped.javaClass)
        }
    }

    private fun matches(method: MethodDescriptor, args: List<Any?>): Boolean {

        if (method.parameters.size != args.size) return false

        return method.parameters.indices.all { i ->
            val arg = args[i]
            val paramType = method.parameters[i].type

            canConvert(arg, paramType)
        }
    }

    private fun conversionScore(value: Any?, targetType: Class<*>): Int? {
        val unwrapped = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> return 10
            else -> value
        }

        if (unwrapped == null) {
            return if (targetType.isPrimitive) null else 0
        }

        return when (targetType) {
            Int::class.javaPrimitiveType,
            Int::class.java -> when (unwrapped) {
                is Int -> 0
                is String -> unwrapped.toIntOrNull()?.let { 1 }
                else -> null
            }

            Double::class.javaPrimitiveType,
            Double::class.java -> when (unwrapped) {
                is Double -> 0
                is Int -> 1
                is String -> unwrapped.toDoubleOrNull()?.let { 2 }
                else -> null
            }

            String::class.java -> when (unwrapped) {
                is String -> 0
                else -> 3
            }

            else -> if (targetType.isAssignableFrom(unwrapped.javaClass)) 0 else null
        }
    }

    private fun matchScore(method: MethodDescriptor, args: List<Any?>): Int? {
        if (method.parameters.size != args.size) return null

        var total = 0
        for (i in method.parameters.indices) {
            val score = conversionScore(args[i], method.parameters[i].type) ?: return null
            total += score
        }
        return total
    }

    fun call(
        target: Any,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {

        val methods = buildMethods(
            collectMethods(target.javaClass, inheritanceLevel)
        ).filter { it.name == methodName }

        if (methods.isEmpty()) {
            throw IllegalArgumentException("No method '$methodName' found on ${target.javaClass.name}")
        }

        val matching = methods.filter { matches(it, args) }

        return when {
            matching.isEmpty() -> throw IllegalArgumentException(
                "No matching overload for '$methodName' with args ${args.map { it?.javaClass?.simpleName }}"
            )

            matching.size > 1 -> throw IllegalArgumentException(
                "Ambiguous call to '$methodName'. Matching overloads: ${
                    matching.joinToString(" | ") { signature(it) }
                }"
            )

            else -> matching.first().invoke(ExecutionContext(target), args)
        }
    }

    fun callStatic(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        val methods = buildMethods(
            collectMethods(clazz, inheritanceLevel)
        ).filter { it.name == methodName && it.isStatic }

        if (methods.isEmpty()) {
            throw IllegalArgumentException("No static method '$methodName' found on ${clazz.name}")
        }

        val scored = methods.mapNotNull { method ->
            matchScore(method, args)?.let { score -> method to score }
        }

        if (scored.isEmpty()) {
            throw IllegalArgumentException(
                "No matching static overload for '$methodName' on ${clazz.name}"
            )
        }

        val bestScore = scored.minOf { it.second }
        val best = scored.filter { it.second == bestScore }

        if (best.size > 1) {
            throw IllegalArgumentException(
                "Ambiguous static call to '$methodName'. Matching overloads: ${
                    best.joinToString(" | ") { signature(it.first) }
                }"
            )
        }

        return best.first().first.invoke(ExecutionContext(null), args)
    }

    fun callStaticExact(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        require(args.size == parameterTypes.size) {
            "args size (${args.size}) must match parameterTypes size (${parameterTypes.size})"
        }

        val methods = buildMethods(
            collectMethods(clazz, inheritanceLevel)
        )

        val matchingMethods = methods.filter { it.name == methodName && it.isStatic }

        val method = matchingMethods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw IllegalArgumentException(
            "No static method '$methodName(${parameterTypes.joinToString(", ") { it.simpleName }})' found on ${clazz.name}"
        )

        return method.invoke(ExecutionContext(null), args)
    }

    fun callExact(
        target: Any,
        methodName: String,
        args: List<Any?>,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        require(args.size == parameterTypes.size) {
            "args size (${args.size}) must match parameterTypes size (${parameterTypes.size})"
        }

        val methods = buildMethods(
            collectMethods(target.javaClass, inheritanceLevel)
        )

        val matchingMethods = methods.filter { it.name == methodName }

        val method = matchingMethods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw IllegalArgumentException(
            buildString {
                append("No method '")
                append(methodName)
                append("(")
                append(parameterTypes.joinToString(", ") { it.simpleName })
                append(")' found on ")
                append(target.javaClass.name)

                if (matchingMethods.isNotEmpty()) {
                    append(". Available overloads: ")
                    append(
                        matchingMethods.joinToString(" | ") { m ->
                            "${m.name}(${m.rawMethod.parameterTypes.joinToString(", ") { it.simpleName }})"
                        }
                    )
                }
            }
        )
        return method.invoke(
            ExecutionContext(target),
            args
        )
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