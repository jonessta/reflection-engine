package au.clef.engine.model

import kotlinx.serialization.Serializable

@Serializable(with = ExecutionIdSerializer::class)
@JvmInline
value class ExecutionId(val value: String) {

    override fun toString(): String = value
}