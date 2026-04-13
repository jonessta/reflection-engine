package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier

class App {

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

        return result.filter {
            Modifier.isPublic(it.modifiers)
        }.distinctBy {
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

            // 🔥 IMPORTANT: capture into local variable

            val paramsCopy = params
            val isStaticCopy = isStatic
            val raw = method

            MethodDescriptor(
                name = method.name,
                parameters = paramsCopy,
                returnType = method.returnType,
                isStatic = isStaticCopy,
                rawMethod = raw,

                invoke = { ctx, args ->

                    if (args.size != paramsCopy.size) {
                        throw IllegalArgumentException(
                            "Expected ${paramsCopy.size}, got ${args.size}"
                        )
                    }

                    val target = when {
                        isStaticCopy -> null
                        ctx.instance != null -> ctx.instance
                        else -> throw IllegalStateException("Instance required")
                    }

                    raw.invoke(target, *args.toTypedArray())
                }
            )
        }
    }
}