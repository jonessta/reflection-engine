package au.clef

class MetadataValidator(
    private val methodRegistry: MethodRegistry = MethodRegistry()
) {

    fun validate(metadata: MetadataRoot): List<ValidationIssue> {
        val issues: MutableList<ValidationIssue> = mutableListOf()

        metadata.classes.forEach { (className: String, classMetadata: ClassMetadata) ->
            val clazz: Class<*> = try {
                Class.forName(className)
            } catch (_: ClassNotFoundException) {
                issues += ValidationIssue(
                    severity = Severity.ERROR,
                    location = className,
                    message = "Class not found"
                )
                return@forEach
            }

            val descriptors: List<MethodDescriptor> =
                methodRegistry.descriptors(clazz, InheritanceLevel.All)

            val descriptorMap: Map<String, MethodDescriptor> =
                descriptors.associateBy { buildMethodKey(it) }

            classMetadata.methods.forEach { (methodKey: String, methodMetadata: MethodMetadata) ->
                val descriptor: MethodDescriptor? = descriptorMap[methodKey]

                if (descriptor == null) {
                    issues += ValidationIssue(
                        severity = Severity.ERROR,
                        location = "$className::$methodKey",
                        message = "Method signature not found"
                    )
                    return@forEach
                }

                if (methodMetadata.parameters.size > descriptor.parameters.size) {
                    issues += ValidationIssue(
                        severity = Severity.ERROR,
                        location = "$className::$methodKey",
                        message = "Metadata defines ${methodMetadata.parameters.size} parameters but method has ${descriptor.parameters.size}"
                    )
                }

                methodMetadata.parameters.forEachIndexed { index: Int, paramMetadata: ParamMetadata ->
                    if (index >= descriptor.parameters.size) return@forEachIndexed

                    if (paramMetadata.name != null && paramMetadata.name.isBlank()) {
                        issues += ValidationIssue(
                            severity = Severity.WARNING,
                            location = "$className::$methodKey:param[$index]",
                            message = "Parameter name is blank"
                        )
                    }

                    if (paramMetadata.label != null && paramMetadata.label.isBlank()) {
                        issues += ValidationIssue(
                            severity = Severity.WARNING,
                            location = "$className::$methodKey:param[$index]",
                            message = "Parameter label is blank"
                        )
                    }
                }
            }
        }

        return issues
    }

    private fun buildMethodKey(descriptor: MethodDescriptor): String {
        val paramTypes: String =
            descriptor.method.parameterTypes.joinToString(",") { it.name }
        return "${descriptor.name}($paramTypes)"
    }
}

data class ValidationIssue(
    val severity: Severity,
    val location: String,
    val message: String
)

enum class Severity {
    WARNING,
    ERROR
}