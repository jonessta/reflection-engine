package au.clef.engine.model

import kotlin.reflect.KClass

sealed class Value {

    data class Scalar(val value: Any?) : Value()

    data class Instance(val obj: Any) : Value()

    data class Record(val type: Class<*>, val fields: Map<String, Value>) : Value()

    data class ListValue(val items: List<Value>) : Value()

    data class MapValue(val entries: Map<String, Value>) : Value()

    data object Null : Value()
}

object Values {
    fun scalar(value: Any?) = Value.Scalar(value)

    fun recordJava(type: Class<*>, vararg fields: Pair<String, Value>) =
        Value.Record(type, linkedMapOf(*fields))

    fun record(type: KClass<*>, vararg fields: Pair<String, Value>) =
        Value.Record(type.java, linkedMapOf(*fields))

    fun list(vararg items: Value) =
        Value.ListValue(items.toList())

    fun map(vararg entries: Pair<String, Value>) =
        Value.MapValue(linkedMapOf(*entries))
}