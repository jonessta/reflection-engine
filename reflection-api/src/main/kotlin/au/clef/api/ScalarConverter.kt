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
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass

interface ScalarConverter<T : Any> {
    val type: KClass<T>
    fun encode(value: T): JsonPrimitive
    fun decode(text: String): T
}

inline fun <reified T : Any> scalarConverter(
    noinline encode: (T) -> JsonPrimitive,
    noinline decode: (String) -> T
): ScalarConverter<T> =
    object : ScalarConverter<T> {
        override val type: KClass<T> = T::class

        override fun encode(value: T): JsonPrimitive =
            encode(value)

        override fun decode(text: String): T =
            decode(text)
    }

object DefaultScalarConverters {

    val all: List<ScalarConverter<out Any>> = listOf(
        scalarConverter<String>(
            encode = { value: String -> JsonPrimitive(value) },
            decode = { text: String -> text }
        ),
        scalarConverter<Int>(
            encode = { value: Int -> JsonPrimitive(value) },
            decode = { text: String -> text.toInt() }
        ),
        scalarConverter<Long>(
            encode = { value: Long -> JsonPrimitive(value) },
            decode = { text: String -> text.toLong() }
        ),
        scalarConverter<Double>(
            encode = { value: Double -> JsonPrimitive(value) },
            decode = { text: String -> text.toDouble() }
        ),
        scalarConverter<Float>(
            encode = { value: Float -> JsonPrimitive(value) },
            decode = { text: String -> text.toFloat() }
        ),
        scalarConverter<Short>(
            encode = { value: Short -> JsonPrimitive(value.toInt()) },
            decode = { text: String -> text.toShort() }
        ),
        scalarConverter<Byte>(
            encode = { value: Byte -> JsonPrimitive(value.toInt()) },
            decode = { text: String -> text.toByte() }
        ),
        scalarConverter<Boolean>(
            encode = { value: Boolean -> JsonPrimitive(value) },
            decode = { text: String -> text.toBooleanStrict() }
        ),
        scalarConverter<Char>(
            encode = { value: Char -> JsonPrimitive(value.toString()) },
            decode = { text: String ->
                require(text.length == 1) { "Invalid char: $text" }
                text[0]
            }
        ),
        scalarConverter<UUID>(
            encode = { value: UUID -> JsonPrimitive(value.toString()) },
            decode = { text: String -> UUID.fromString(text) }
        ),
        scalarConverter<URI>(
            encode = { value: URI -> JsonPrimitive(value.toString()) },
            decode = { text: String -> URI.create(text) }
        ),
        scalarConverter<URL>(
            encode = { value: URL -> JsonPrimitive(value.toString()) },
            decode = { text: String -> URI.create(text).toURL() }
        ),
        scalarConverter<Instant>(
            encode = { value: Instant -> JsonPrimitive(value.toString()) },
            decode = { text: String -> Instant.parse(text) }
        ),
        scalarConverter<LocalDate>(
            encode = { value: LocalDate -> JsonPrimitive(value.toString()) },
            decode = { text: String -> LocalDate.parse(text) }
        ),
        scalarConverter<LocalDateTime>(
            encode = { value: LocalDateTime -> JsonPrimitive(value.toString()) },
            decode = { text: String -> LocalDateTime.parse(text) }
        ),
        scalarConverter<LocalTime>(
            encode = { value: LocalTime -> JsonPrimitive(value.toString()) },
            decode = { text: String -> LocalTime.parse(text) }
        ),
        scalarConverter<Path>(
            encode = { value: Path -> JsonPrimitive(value.toString()) },
            decode = { text: String -> Paths.get(text) }
        ),
        scalarConverter<Locale>(
            encode = { value: Locale -> JsonPrimitive(value.toLanguageTag()) },
            decode = { text: String -> Locale.forLanguageTag(text) }
        ),
        scalarConverter<Currency>(
            encode = { value: Currency -> JsonPrimitive(value.currencyCode) },
            decode = { text: String -> Currency.getInstance(text) }
        )
    )
}