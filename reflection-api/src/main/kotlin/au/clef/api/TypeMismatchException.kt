package au.clef.api

import au.clef.api.model.Value

class TypeMismatchException(
    value: Value,
    targetType: Class<*>
) : RuntimeException("Cannot convert $value to ${targetType.name}")
