package au.clef.api

class ScalarTypeRegistry(
    userDefinedConverters: List<ScalarConverter<out Any>> = emptyList()
) {
    private val decoderMap: Map<Class<*>, ScalarConverter<out Any>> =
        (userDefinedConverters + DefaultScalarConverters.all)
            .associateBy { converter: ScalarConverter<out Any> ->
                converter.type.javaObjectType
            }

    fun isScalarLike(type: Class<*>): Boolean {
        val wrapped: Class<*> = wrapPrimitive(type)
        return wrapped.isEnum || decoderMap.containsKey(wrapped)
    }

    @Suppress("UNCHECKED_CAST")
    fun decoderFor(targetType: Class<*>): ScalarConverter<Any>? =
        decoderMap[wrapPrimitive(targetType)] as ScalarConverter<Any>?

    @Suppress("UNCHECKED_CAST")
    fun encoderFor(value: Any): ScalarConverter<Any>? =
        decoderMap.values.firstOrNull { converter: ScalarConverter<out Any> ->
            converter.type.javaObjectType.isInstance(value)
        } as ScalarConverter<Any>?

    fun wrapPrimitive(type: Class<*>): Class<*> =
        if (type.isPrimitive && type != Void.TYPE) type.kotlin.javaObjectType else type
}