package au.clef.engine.model

@JvmInline
value class ExecutionId(val value: String) {

    override fun toString(): String = value
}