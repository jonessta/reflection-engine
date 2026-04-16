package au.clef.app.model

class OverloadedService {

    fun format(value: Int): String {
        return "Int: $value"
    }

    fun format(value: String): String {
        return "String: $value"
    }
}