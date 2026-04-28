package au.clef.api

import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass
import java.time.Instant

interface ScalarConverter<T : Any> {
    val type: KClass<T>
    fun encode(value: T): JsonPrimitive
    fun decode(text: String): T
}

object DefaultScalarConverters {
    val all: List<ScalarConverter<out Any>> = listOf(
        scalarConverter(String::class, { JsonPrimitive(it) }, { it }),
        scalarConverter(Int::class, { JsonPrimitive(it) }, { it.toInt() }),
        scalarConverter(Long::class, { JsonPrimitive(it) }, { it.toLong() }),
        scalarConverter(Double::class, { JsonPrimitive(it) }, { it.toDouble() }),
        scalarConverter(Float::class, { JsonPrimitive(it) }, { it.toFloat() }),
        scalarConverter(Short::class, { JsonPrimitive(it) }, { it.toShort() }),
        scalarConverter(Byte::class, { JsonPrimitive(it) }, { it.toByte() }),
        scalarConverter(Boolean::class, { JsonPrimitive(it) }, {
            when (it.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid boolean value: $it")
            }
        }),
        scalarConverter(Char::class, { JsonPrimitive(it.toString()) }, {
            require(it.length == 1) { "Invalid char value: $it" }
            it[0]
        }),
        scalarConverter(UUID::class, { JsonPrimitive(it.toString()) }, { UUID.fromString(it) }),
        scalarConverter(Instant::class, { JsonPrimitive(it.toString()) }, { Instant.parse(it) }),
        scalarConverter(LocalDate::class, { JsonPrimitive(it.toString()) }, { LocalDate.parse(it) }),
        scalarConverter(LocalDateTime::class, { JsonPrimitive(it.toString()) }, { LocalDateTime.parse(it) }),
        scalarConverter(LocalTime::class, { JsonPrimitive(it.toString()) }, { LocalTime.parse(it) }),
        scalarConverter(URI::class, { JsonPrimitive(it.toString()) }, { URI.create(it) }),
        scalarConverter(URL::class, { JsonPrimitive(it.toString()) }, { URI.create(it).toURL() }),
        scalarConverter(Path::class, { JsonPrimitive(it.toString()) }, { Paths.get(it) }),
        scalarConverter(Locale::class, { JsonPrimitive(it.toString()) }, { Locale.forLanguageTag(it) }),
        scalarConverter(Currency::class, { JsonPrimitive(it.currencyCode) }, { Currency.getInstance(it) })
    )
}

fun <T : Any> scalarConverter(
    type: KClass<T>,
    encode: (T) -> JsonPrimitive,
    decode: (String) -> T
): ScalarConverter<T> =
    object : ScalarConverter<T> {
        override val type: KClass<T> = type
        override fun encode(value: T): JsonPrimitive = encode(value)
        override fun decode(text: String): T = decode(text)
    }