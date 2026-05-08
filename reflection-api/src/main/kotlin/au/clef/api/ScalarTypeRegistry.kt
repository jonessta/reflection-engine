package au.clef.api

import au.clef.engine.TerminalTypeDecider

class ScalarTypeRegistry(userDefinedConverters: List<ScalarConverter<out Any>> = emptyList())
    : TerminalTypeDecider {

    private val decoderMap: Map<Class<*>, ScalarConverter<out Any>> =
        (DefaultScalarConverters.all + userDefinedConverters)
            .associateBy { converter: ScalarConverter<out Any> -> converter.type.javaObjectType }

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

    fun wrapPrimitive(type: Class<*>): Class<*> = type.kotlin.javaObjectType

    override fun isTerminal(type: Class<*>): Boolean {
        if (type.isPrimitive) return true
        if (type.isEnum) return true
        if (type == String::class.java) return true
        if (type == Any::class.java || type == Object::class.java) return true
        if (isScalarLike(type)) return true
        if (type.name.startsWith("java.")) return true
        if (type.name.startsWith("javax.")) return true
        if (type.name.startsWith("kotlin.")) return true
        return false
    }
}