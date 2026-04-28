package au.clef.engine.model

sealed class Value {

    data class Scalar(val value: Any?) : Value()

    data class Instance(val obj: Any) : Value()

    data class Record(
        val type: Class<*>,
        val fields: Map<String, Value>
    ) : Value()

    data class ListValue(
        val items: List<Value>
    ) : Value()

    data class MapValue(
        val entries: List<MapEntry>
    ) : Value()

    data object Null : Value()
}

data class MapEntry(
    val key: Value,
    val value: Value
)
