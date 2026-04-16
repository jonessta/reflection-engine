package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class MethodRegistry {

    private data class CacheKey(val clazz: Class<*>, val inheritanceLevel: InheritanceLevel)

    private val descriptorCache: MutableMap<CacheKey, List<MethodDescriptor>> = ConcurrentHashMap()

    fun bindings(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodBinding> {
        val methods = collectMethods(clazz, inheritanceLevel)

        return methods.map { method ->
            val isStatic = Modifier.isStatic(method.modifiers)
            val params = method.parameters.mapIndexed { i, p ->
                ParamDescriptor(
                    index = i,
                    name = p.name ?: "arg$i",
                    label = null,
                    type = p.type.name,
                    nullable = true
                )
            }
            val descriptor = MethodDescriptor(
                id = buildId(clazz, method),
                name = method.name,
                parameters = params,
                returnType = method.returnType.name,
                isStatic = isStatic
            )

            MethodBinding(descriptor = descriptor, method = method)
        }
    }

    private fun buildId(clazz: Class<*>, method: Method): String {
        val paramTypes = method.parameterTypes.joinToString(",") { it.name }
        return "${clazz.name}#${method.name}($paramTypes)"
    }

    fun clearCache() {
        descriptorCache.clear()
    }

    private fun collectMethods(
        clazz: Class<*>,
        level: InheritanceLevel
    ): List<Method> {
        val result: MutableList<Method> = mutableListOf()
        var current: Class<*>? = clazz
        var depth: Int = 0
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
        return result
            .filter { method: Method ->
                Modifier.isPublic(method.modifiers) &&
                        !method.isSynthetic &&
                        !method.isBridge
            }
            .distinctBy { method: Method ->
                "${method.name}(${method.parameterTypes.joinToString(",") { it.name }})"
            }
    }
}