package au.clef.api

// todo rename file to ClassResolver or put these classes with other classes
sealed class ResolvedType {
    data class Scalar(val type: Class<*>) : ResolvedType()
    data class Structured(val type: Class<*>) : ResolvedType()
}

interface ClassResolver {
    fun resolve(typeName: String): ResolvedType
}
