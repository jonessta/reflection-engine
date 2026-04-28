package au.clef.api

import kotlinx.serialization.json.JsonPrimitive

interface ScalarValueEncoder {
    fun canEncode(value: Any): Boolean
    fun encode(value: Any): JsonPrimitive
}

class SimpleScalarValueEncoder(
    private val predicate: (Any) -> Boolean,
    private val encoder: (Any) -> JsonPrimitive
) : ScalarValueEncoder {

    override fun canEncode(value: Any): Boolean =
        predicate(value)

    override fun encode(value: Any): JsonPrimitive =
        encoder(value)
}

fun scalarValueEncoder(
    predicate: (Any) -> Boolean,
    encoder: (Any) -> JsonPrimitive
): ScalarValueEncoder =
    SimpleScalarValueEncoder(predicate, encoder)