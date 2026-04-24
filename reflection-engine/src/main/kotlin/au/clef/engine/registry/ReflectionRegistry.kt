package au.clef.engine.registry

import au.clef.engine.ExposedTarget
import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private data class RegistryEntry(
    val descriptor: MethodDescriptor,
    val method: Method
)

class ReflectionRegistry(
    targets: Collection<ExposedTarget>,
    supportingClasses: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : ReflectionTypes {

    private val descriptorsByClass: MutableMap<Class<*>, List<MethodDescriptor>> = ConcurrentHashMap()
    private val entriesById: MutableMap<MethodId, RegistryEntry> = ConcurrentHashMap()
    override val targetClasses: List<Class<*>> = targets.map { it.targetClass.java }.distinct()

    override val classes: List<Class<*>> = (targets.map { it.targetClass } + supportingClasses)
        .distinct()
        .map { it.java }

    init {
        require(targetClasses.isNotEmpty()) { "targets must not be empty" }
        targetClasses.forEach { registerClass(it) }
    }

    fun registerClass(clazz: KClass<*>) = registerClass(clazz.java)

    fun registerClass(clazz: Class<*>) {
        if (descriptorsByClass.containsKey(clazz))
            return

        val methods: List<Method> = collectHierarchyMethods(clazz, inheritanceLevel)
        val descriptors: MutableList<MethodDescriptor> = mutableListOf()
        for (method: Method in methods) {
            val methodId: MethodId = MethodId.from(method)
            val descriptor: MethodDescriptor = MethodDescriptor.from(method, methodId)
            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(descriptor, method)
        }
        descriptorsByClass[clazz] = descriptors
    }

    private fun collectHierarchyMethods(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<Method> {
        val methods: MutableList<Method> = mutableListOf()
        var current: Class<*>? = clazz
        var depth = 0
        while (current != null && depth <= inheritanceLevel.depth) {
            methods += current.declaredMethods
            current = current.superclass
            depth++
        }
        return methods.distinctBy(MethodId::from)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]
            ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor
            ?: throw MethodNotFoundException(
                methodId = id,
                available = entriesById.keys.map(MethodId::toString)
            )

    fun method(id: MethodId): Method =
        entriesById[id]?.method
            ?: throw MethodNotFoundException(
                methodId = id,
                available = entriesById.keys.map(MethodId::toString)
            )

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values.map(RegistryEntry::descriptor)
}