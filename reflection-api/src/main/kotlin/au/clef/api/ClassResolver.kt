package au.clef.api

sealed class ResolvedType {
    data class Scalar(val type: Class<*>) : ResolvedType()
    data class Structured(val type: Class<*>) : ResolvedType()
}

interface ClassResolver {
    fun resolve(typeName: String): ResolvedType
}
