package au.clef.api.model

sealed class Value {

    data class Scalar(
        val value: ScalarValue
    ) : Value()

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

sealed class ScalarValue {

    data class StringValue(
        val value: String
    ) : ScalarValue()

    data class BooleanValue(
        val value: Boolean
    ) : ScalarValue()

    /**
     * Canonical numeric scalar representation.
     * Keep the lexical form so later conversion can decide whether
     * the target should be Int, Long, Double, Float, etc.
     */
    data class NumberValue(
        val value: String
    ) : ScalarValue()
}