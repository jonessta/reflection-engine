package au.clef.api

class ScalarTypeRegistry(
    userDefinedConverters: List<ScalarConverter<out Any>> = emptyList()
) {
    // Map for O(1) decoder lookups
    private val decoderMap: Map<Class<*>, ScalarConverter<out Any>> =
        (userDefinedConverters + DefaultScalarConverters.all)
            .associateBy { it.type.javaObjectType }

    fun isScalarLike(type: Class<*>): Boolean {
        val wrapped = wrapPrimitive(type)
        return wrapped.isEnum || decoderMap.containsKey(wrapped)
    }

    @Suppress("UNCHECKED_CAST")
    fun decoderFor(targetType: Class<*>): ScalarConverter<Any>? =
        decoderMap[wrapPrimitive(targetType)] as ScalarConverter<Any>?

    @Suppress("UNCHECKED_CAST")
    fun encoderFor(value: Any): ScalarConverter<Any>? =
        // Encoders still need a search for inheritance/polymorphism support
        decoderMap.values.firstOrNull { it.type.javaObjectType.isInstance(value) } as ScalarConverter<Any>?

    // Simplified using Kotlin's built-in javaObjectType property
    fun wrapPrimitive(type: Class<*>): Class<*> =
        if (type.isPrimitive && type != Void.TYPE) type.kotlin.javaObjectType else type
}