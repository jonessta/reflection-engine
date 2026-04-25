package au.clef.engine.registry

import au.clef.engine.ExposedTarget
import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private data class RegistryEntry(
    val descriptor: MethodDescriptor,
    val method: Method
)

class ReflectionRegistry(
    targets: Collection<ExposedTarget>,
    supportingTypes: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : ReflectionTypes {

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> = ConcurrentHashMap()
    private val entriesById: MutableMap<MethodId, RegistryEntry> = ConcurrentHashMap()

    override val targetClasses: List<Class<*>> =
        targets.map { it.targetClass.java }.distinct()

    override val classes: List<Class<*>> =
        (targets.map { it.targetClass } + supportingTypes)
            .distinct()
            .map { it.java }

    init {
        require(targets.isNotEmpty()) { "targets must not be empty" }
        targets.forEach(::registerTarget)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList()
            ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> =
        descriptors(clazz.java)

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

    fun allDescriptors(): List<MethodDescriptor> =
        entriesById.values.map { it.descriptor }

    private fun registerTarget(target: ExposedTarget) {
        when (target) {
            is ExposedTarget.Instance ->
                registerMethods(
                    clazz = target.targetClass.java,
                    predicate = { method -> !Modifier.isStatic(method.modifiers) }
                )

            is ExposedTarget.StaticClass ->
                registerMethods(
                    clazz = target.targetClass.java,
                    predicate = { method -> Modifier.isStatic(method.modifiers) }
                )

            is ExposedTarget.StaticMethod ->
                registerSingleMethod(target.targetClass.java, target.methodId)
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        predicate: (Method) -> Boolean
    ) {
        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        collectHierarchyMethods(clazz, inheritanceLevel)
            .filter(predicate)
            .forEach { method ->
                val methodId = MethodId.from(method)
                if (entriesById.containsKey(methodId)) return@forEach

                val descriptor = MethodDescriptor.from(method)
                descriptors += descriptor
                entriesById[methodId] = RegistryEntry(
                    descriptor = descriptor,
                    method = method
                )
            }
    }

    private fun registerSingleMethod(clazz: Class<*>, methodId: MethodId) {
        val method = resolveMethod(clazz, methodId)
        require(Modifier.isStatic(method.modifiers)) {
            "Method ${methodId.value} must be static"
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        if (entriesById.containsKey(methodId)) return

        val descriptor = MethodDescriptor.from(method)
        descriptors += descriptor
        entriesById[methodId] = RegistryEntry(
            descriptor = descriptor,
            method = method
        )
    }

    private fun resolveMethod(clazz: Class<*>, methodId: MethodId): Method {
        val methods = collectHierarchyMethods(clazz, inheritanceLevel)
        return methods.firstOrNull { MethodId.from(it) == methodId }
            ?: throw MethodNotFoundException(
                methodId = methodId,
                available = methods.map { MethodId.from(it).toString() }
            )
    }

    private fun collectHierarchyMethods(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        var depth = 0
        while (current != null && depth <= inheritanceLevel.depth) {
            methods += current.declaredMethods
            current = current.superclass
            depth++
        }
        return methods.distinctBy(MethodId::from)
    }
}