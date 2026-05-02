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
import java.util.Currency
import java.util.Locale
import java.util.UUID
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

        override fun encode(value: T): ScalarValue =
            encode(value)

        override fun decode(value: ScalarValue): T =
            decode(value)
    }

object DefaultScalarConverters {

    val all: List<ScalarConverter<out Any>> = listOf(
        scalarConverter<String>(
            encode = { value: String ->
                ScalarValue.StringValue(value)
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> value.value
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<Int>(
            encode = { value: Int ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toInt()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Long>(
            encode = { value: Long ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toLong()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Double>(
            encode = { value: Double ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toDouble()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Float>(
            encode = { value: Float ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toFloat()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Short>(
            encode = { value: Short ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toShort()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Byte>(
            encode = { value: Byte ->
                ScalarValue.NumberValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.NumberValue -> value.value.toByte()
                    else -> throw IllegalArgumentException("Expected numeric scalar")
                }
            }
        ),
        scalarConverter<Boolean>(
            encode = { value: Boolean ->
                ScalarValue.BooleanValue(value)
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.BooleanValue -> value.value
                    else -> throw IllegalArgumentException("Expected boolean scalar")
                }
            }
        ),
        scalarConverter<Char>(
            encode = { value: Char ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> {
                        require(value.value.length == 1) { "Invalid char: ${value.value}" }
                        value.value[0]
                    }
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<UUID>(
            encode = { value: UUID ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> UUID.fromString(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<URI>(
            encode = { value: URI ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> URI.create(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<URL>(
            encode = { value: URL ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> URI.create(value.value).toURL()
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<Instant>(
            encode = { value: Instant ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> Instant.parse(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<LocalDate>(
            encode = { value: LocalDate ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> LocalDate.parse(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<LocalDateTime>(
            encode = { value: LocalDateTime ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> LocalDateTime.parse(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<LocalTime>(
            encode = { value: LocalTime ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> LocalTime.parse(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<Path>(
            encode = { value: Path ->
                ScalarValue.StringValue(value.toString())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> Paths.get(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<Locale>(
            encode = { value: Locale ->
                ScalarValue.StringValue(value.toLanguageTag())
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> Locale.forLanguageTag(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        ),
        scalarConverter<Currency>(
            encode = { value: Currency ->
                ScalarValue.StringValue(value.currencyCode)
            },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> Currency.getInstance(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar")
                }
            }
        )
    )
}