package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.ExposedTarget
import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private data class RegistryEntry(val descriptor: MethodDescriptor, val method: Method)

class ReflectionRegistry(
    targets: Collection<ExposedTarget>,
    supportingTypes: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : ReflectionTypes {

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> = ConcurrentHashMap()
    private val entriesById: MutableMap<MethodId, RegistryEntry> = ConcurrentHashMap()
    private val executionContextsById: MutableMap<ExecutionId, ExecutionContext> = ConcurrentHashMap()

    override val targetClasses: List<Class<*>> = targets.map { it.targetClass.java }.distinct()

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

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun method(id: MethodId): Method =
        entriesById[id]?.method
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        executionContextsById[executionId]
            ?: throw IllegalArgumentException("Unknown executionId: ${executionId.value}")

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values.map { it.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> = executionContextsById.values.toList()

    private fun registerTarget(target: ExposedTarget) {
        when (target) {
            is ExposedTarget.Instance ->
                registerMethods(
                    clazz = target.targetClass.java,
                    predicate = { method -> !Modifier.isStatic(method.modifiers) },
                    executionContextFor = { methodId ->
                        ExecutionContext.Instance(target.id, methodId)
                    }
                )

            is ExposedTarget.InstanceMethod ->
                registerSingleMethod(
                    clazz = target.targetClass.java,
                    methodId = target.methodId,
                    requireStatic = false,
                    executionContext = ExecutionContext.Instance(target.id, target.methodId)
                )

            is ExposedTarget.StaticClass ->
                registerMethods(
                    clazz = target.targetClass.java,
                    predicate = { method -> Modifier.isStatic(method.modifiers) },
                    executionContextFor = { methodId ->
                        ExecutionContext.Static(methodId)
                    }
                )

            is ExposedTarget.StaticMethod ->
                registerSingleMethod(
                    clazz = target.targetClass.java,
                    methodId = target.methodId,
                    requireStatic = true,
                    executionContext = ExecutionContext.Static(target.methodId)
                )
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        predicate: (Method) -> Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val descriptors: MutableList<MethodDescriptor> = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        collectHierarchyMethods(clazz, inheritanceLevel)
            .filter(predicate)
            .forEach { method ->
                val methodId = MethodId.from(method)
                if (entriesById.containsKey(methodId))
                    return@forEach
                val descriptor: MethodDescriptor = MethodDescriptor.from(method)
                descriptors += descriptor
                entriesById[methodId] = RegistryEntry(descriptor = descriptor, method = method)
                val executionContext = executionContextFor(methodId)
                executionContextsById[executionContext.executionId] = executionContext
            }
    }

    private fun registerSingleMethod(
        clazz: Class<*>,
        methodId: MethodId,
        requireStatic: Boolean,
        executionContext: ExecutionContext
    ) {
        val method = resolveMethod(clazz, methodId)
        if (requireStatic) {
            require(Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be static" }
        } else {
            require(!Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be an instance method" }
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        if (!entriesById.containsKey(methodId)) {
            val descriptor = MethodDescriptor.from(method)
            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(descriptor = descriptor, method = method)
        }

        executionContextsById[executionContext.executionId] = executionContext
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