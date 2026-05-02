package au.clef.api

import au.clef.api.model.ScalarValue
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
    fun encode(value: T): ScalarValue
    fun decode(value: ScalarValue): T
}

inline fun <reified T : Any> scalarConverter(
    noinline encode: (T) -> ScalarValue,
    noinline decode: (ScalarValue) -> T
): ScalarConverter<T> =
    object : ScalarConverter<T> {
        override val type: KClass<T> = T::class
        override fun encode(value: T): ScalarValue = encode(value)
        override fun decode(value: ScalarValue): T = decode(value)
    }

inline fun <reified T : Any> stringScalarConverter(
    noinline encodeText: (T) -> String = { value: T -> value.toString() },
    noinline decodeText: (String) -> T
): ScalarConverter<T> = scalarConverter(
    encode = { value: T -> ScalarValue.StringValue(encodeText(value)) },
    decode = { value: ScalarValue ->
        when (value) {
            is ScalarValue.StringValue -> decodeText(value.value)
            else -> throw IllegalArgumentException("Expected string scalar")
        }
    }
)

private inline fun <reified T : Any> numberScalarConverter(
    noinline encodeText: (T) -> String = { value: T -> value.toString() },
    noinline decodeText: (String) -> T
): ScalarConverter<T> =
    scalarConverter(
        encode = { value: T -> ScalarValue.NumberValue(encodeText(value)) },
        decode = { value: ScalarValue ->
            when (value) {
                is ScalarValue.NumberValue -> decodeText(value.value)
                else -> throw IllegalArgumentException("Expected numeric scalar")
            }
        }
    )

object DefaultScalarConverters {

    val all: List<ScalarConverter<out Any>> = listOf(
        stringScalarConverter<String> { text: String -> text },
        numberScalarConverter<Int> { text: String -> text.toInt() },
        numberScalarConverter<Long> { text: String -> text.toLong() },
        numberScalarConverter<Double> { text: String -> text.toDouble() },
        numberScalarConverter<Float> { text: String -> text.toFloat() },
        numberScalarConverter<Short> { text: String -> text.toShort() },
        numberScalarConverter<Byte> { text: String -> text.toByte() },
        scalarConverter<Boolean>(
            encode = { value: Boolean ->
                ScalarValue.BooleanValue(value)
            },
            decode = { value: ScalarValue ->
                if (value is ScalarValue.BooleanValue) value.value
                else throw IllegalArgumentException("Expected boolean scalar")
            }
        ),
        stringScalarConverter<Char>(
            encodeText = Char::toString,
            decodeText = { text: String ->
                require(text.length == 1) { "Invalid char: $text" }
                text[0]
            }
        ),
        stringScalarConverter<UUID>(decodeText = UUID::fromString),
        stringScalarConverter<URI>(decodeText = URI::create),
        stringScalarConverter<URL>(decodeText = { text: String ->
            URI.create(text).toURL()
        }),
        stringScalarConverter<Instant>(decodeText = Instant::parse),
        stringScalarConverter<LocalDate>(decodeText = LocalDate::parse),
        stringScalarConverter<LocalDateTime>(decodeText = LocalDateTime::parse),
        stringScalarConverter<LocalTime>(decodeText = LocalTime::parse),
        stringScalarConverter<Path>(decodeText = Paths::get),
        stringScalarConverter<Locale>(
            encodeText = Locale::toLanguageTag,
            decodeText = Locale::forLanguageTag
        ),
        stringScalarConverter<Currency>(
            encodeText = { value: Currency -> value.currencyCode },
            decodeText = Currency::getInstance
        )
    )
}