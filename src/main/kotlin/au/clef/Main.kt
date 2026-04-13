package au.clef

fun main(args: Array<String>) {

    val app = App()

    // 1. Collect methods (inheritance-aware)
    val rawMethods = app.collectMethods(
        java.lang.Math::class.java,
        InheritanceLevel.All
    )

    // 2. Build safe descriptors
    val methods = app.buildMethods(rawMethods)

    // 3. Find method
    val maxMethod = methods.first {
        it.name == "max" &&
                it.rawMethod.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                )
    }

    println(maxMethod)
    // 4. Invoke safely (NO STRING KEYS)
    val result = maxMethod.invoke(
        ExecutionContext(null),
        listOf(10, 20)
    )

    println(result) // 20
}