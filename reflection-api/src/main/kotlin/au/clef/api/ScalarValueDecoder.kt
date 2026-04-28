package au.clef.api

interface ScalarValueDecoder {
    fun canDecode(rawValue: Any, targetType: Class<*>): Boolean
    fun decode(rawValue: Any, targetType: Class<*>): Any?
}