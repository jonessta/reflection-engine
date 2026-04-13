package au.clef

import java.lang.reflect.Method


fun main(args: Array<String>) {
    val app = App()

    // 1. Collect methods (inheritance-aware)
    val rawMethods: List<Method> = app.collectMethods(
        Math::class.java,
        InheritanceLevel.All
    )

    // 2. Build safe descriptors
    val methods: List<MethodDescriptor> = app.buildMethods(rawMethods)

    // 3. Find method (same logic, but now descriptor-based)
    val maxMethod = methods.first {
        it.name == "max" && it.parameters.size == 2
    }

    // 4. BULLETPROOF invocation (NO STRING KEYS)
    val result = maxMethod.invoke(
        ExecutionContext(null), // Math = static methods only
        listOf(10, 20)
    )

    println(result) // 20
}