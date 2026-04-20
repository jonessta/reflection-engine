package au.clef.engine.model

sealed class Value {

    data class Primitive(val value: Any?) : Value()

    data class Instance(val obj: Any) : Value()

    data class Object(
        val type: Class<*>,
        val fields: Map<String, Value>
    ) : Value()

    data object Null : Value()
}