package au.clef.api

import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.reflect.KClass

interface ScalarConverter<T : Any> {
    val type: KClass<T>
    fun encode(value: T): JsonPrimitive
    fun decode(text: String): T
}

inline fun <reified T : Any> scalarConverter(
    noinline decode: (String) -> T
): ScalarConverter<T> =
    object : ScalarConverter<T> {
        override val type: KClass<T> = T::class
        override fun encode(value: T): JsonPrimitive = JsonPrimitive(value.toString())
        override fun decode(text: String): T = decode(text)
    }

inline fun <reified T : Any> scalarConverter(
    noinline encode: (T) -> JsonPrimitive,
    noinline decode: (String) -> T
): ScalarConverter<T> =
    object : ScalarConverter<T> {
        override val type: KClass<T> = T::class
        override fun encode(value: T): JsonPrimitive = encode(value)
        override fun decode(text: String): T = decode(text)
    }

object DefaultScalarConverters {
    val all: List<ScalarConverter<out Any>> = listOf(
        scalarConverter<String> { it },
        scalarConverter<Int> { it.toInt() },
        scalarConverter<Long> { it.toLong() },
        scalarConverter<Double> { it.toDouble() },
        scalarConverter<Float> { it.toFloat() },
        scalarConverter<Short> { it.toShort() },
        scalarConverter<Byte> { it.toByte() },
        scalarConverter<Boolean> { it.toBooleanStrict() },
        scalarConverter<Char>(
            encode = { JsonPrimitive(it.toString()) },
            decode = {
                require(it.length == 1) { "Invalid char: $it" }
                it[0]
            }
        ),
        scalarConverter<UUID>(UUID::fromString),
        scalarConverter<URI>(URI::create),
        scalarConverter<URL>(
            encode = { JsonPrimitive(it.toString()) },
            decode = { URI.create(it).toURL() }
        ),
        scalarConverter<Instant>(Instant::parse),
        scalarConverter<LocalDate>(LocalDate::parse),
        scalarConverter<LocalDateTime>(LocalDateTime::parse),
        scalarConverter<LocalTime>(LocalTime::parse),
        scalarConverter<Path>(
            encode = { JsonPrimitive(it.toString()) },
            decode = Paths::get
        ),
        scalarConverter<Locale>(
            encode = { JsonPrimitive(it.toLanguageTag()) },
            decode = Locale::forLanguageTag
        ),
        scalarConverter<Currency>(
            encode = { JsonPrimitive(it.currencyCode) },
            decode = Currency::getInstance
        )
    )
}