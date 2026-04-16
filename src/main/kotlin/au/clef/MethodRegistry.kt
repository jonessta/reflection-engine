package au.clef

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class MethodRegistry {

    private data class CacheKey(val clazz: Class<*>, val inheritanceLevel: InheritanceLevel)

    private val descriptorCache: MutableMap<CacheKey, List<MethodDescriptor>> = ConcurrentHashMap()

    fun descriptors(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> {
        val key = CacheKey(clazz, inheritanceLevel)
        return descriptorCache.getOrPut(key) {
            buildMethods(collectMethods(clazz, inheritanceLevel))
        }
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
            availableOverloads = methods
                .filter { it.name == methodName }
                .map { signature(it) }
        )
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

    private fun buildMethods(methods: List<Method>): List<MethodDescriptor> =
        methods.map { method: Method ->
            val isStatic: Boolean = Modifier.isStatic(method.modifiers)
            val params: List<ParamDescriptor> =
                method.parameters.mapIndexed { i: Int, p ->
                    ParamDescriptor(
                        index = i,
                        name = p.name ?: "arg$i",
                        label = null,
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