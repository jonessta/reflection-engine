package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.kotlinFunction

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
        return result
            .filter { Modifier.isPublic(it.modifiers) }
            .distinctBy {
                it.name + it.parameterTypes.joinToString { t -> t.name }
            }
    }

    fun buildMethods(methods: List<java.lang.reflect.Method>): List<MethodDescriptor> {

        return methods.map { method ->

            val isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers)

            val params = method.parameters.mapIndexed { index, p ->
                ParamDescriptor(
                    index = index,
                    type = p.type,
                    nullable = true
                )
            }

            MethodDescriptor(
                name = method.name,
                parameters = params,
                returnType = method.returnType,
                isStatic = isStatic,
                rawMethod = method,

                invoke = { ctx, args ->

                    // 🔒 HARD SAFETY CHECK
                    if (args.size != params.size) {
                        throw IllegalArgumentException(
                            "Argument mismatch for ${method.name}: expected ${params.size}, got ${args.size}"
                        )
                    }

                    val target = when {
                        isStatic -> null
                        ctx.instance != null -> ctx.instance
                        else -> throw IllegalStateException(
                            "Instance required for ${method.name}"
                        )
                    }

                    method.invoke(target, *args.toTypedArray())
                }
            )
        }
    }
}