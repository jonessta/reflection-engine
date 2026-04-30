package au.clef.api

class ScalarTypeRegistry(
    userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
) {
    private val scalarConverters: List<ScalarConverter<out Any>> =
        userDefinedScalarConverters + DefaultScalarConverters.all

    fun isScalarLike(type: Class<*>): Boolean {
        val wrapped = wrapPrimitive(type)
        return wrapped.isEnum || scalarConverters.any { it.type.javaObjectType == wrapped }
    }

    fun decoderFor(targetType: Class<*>): ScalarConverter<Any>? {
        val wrapped = wrapPrimitive(targetType)

        @Suppress("UNCHECKED_CAST")
        return scalarConverters.firstOrNull { it.type.javaObjectType == wrapped } as ScalarConverter<Any>?
    }

    fun encoderFor(value: Any): ScalarConverter<Any>? {
        @Suppress("UNCHECKED_CAST")
        return scalarConverters.firstOrNull { it.type.javaObjectType.isInstance(value) } as ScalarConverter<Any>?
    }

    fun wrapPrimitive(type: Class<*>): Class<*> =
        when (type) {
            Int::class.javaPrimitiveType -> Int::class.javaObjectType
            Long::class.javaPrimitiveType -> Long::class.javaObjectType
            Double::class.javaPrimitiveType -> Double::class.javaObjectType
            Float::class.javaPrimitiveType -> Float::class.javaObjectType
            Short::class.javaPrimitiveType -> Short::class.javaObjectType
            Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
            Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
            Char::class.javaPrimitiveType -> Char::class.javaObjectType
            Void.TYPE -> Void::class.java
            else -> type
        }
}