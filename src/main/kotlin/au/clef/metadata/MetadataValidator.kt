package au.clef.metadata

import au.clef.engine.model.*
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.model.*

class MetadataValidator(private val methodRegistry: MethodRegistry = MethodRegistry()) {

    fun validate(metadata: MetadataRoot): List<ValidationIssue> {
        val issues: MutableList<ValidationIssue> = mutableListOf()
        val allDescriptors: List<MethodDescriptor> = methodRegistry.allDescriptors()
        val descriptorMap: Map<MethodId, MethodDescriptor> =
            allDescriptors.associateBy { descriptor: MethodDescriptor -> descriptor.id }
        metadata.methods.forEach { (methodId: MethodId, methodMetadata: MethodMetadata) ->
            val descriptor: MethodDescriptor? = descriptorMap[methodId]
            if (descriptor == null) {
                issues += ValidationIssue(
                    severity = Severity.ERROR, location = methodId.toString(), message = "Method not found"
                )
                return@forEach
            }
            if (methodMetadata.parameters.size > descriptor.parameters.size) {
                issues += ValidationIssue(
                    severity = Severity.ERROR,
                    location = methodId.toString(),
                    message = "Metadata defines ${methodMetadata.parameters.size} parameters but method has ${descriptor.parameters.size}"
                )
            }
            methodMetadata.parameters.forEachIndexed { index: Int, paramMetadata: ParamMetadata ->
                if (index >= descriptor.parameters.size) {
                    return@forEachIndexed
                }

                val descriptorParam: ParamDescriptor = descriptor.parameters[index]
                if (paramMetadata.name != null && paramMetadata.name.isBlank()) {
                    issues += ValidationIssue(
                        severity = Severity.WARNING,
                        location = "${methodId}:param[$index]",
                        message = "Parameter name is blank"
                    )
                }
                if (paramMetadata.label != null && paramMetadata.label.isBlank()) {
                    issues += ValidationIssue(
                        severity = Severity.WARNING,
                        location = "${methodId}:param[$index]",
                        message = "Parameter label is blank"
                    )
                }
                if (paramMetadata.name != null && paramMetadata.name == descriptorParam.reflectedName) {
                    issues += ValidationIssue(
                        severity = Severity.WARNING,
                        location = "${methodId}:param[$index]",
                        message = "Parameter name duplicates reflected name"
                    )
                }
            }
        }
        return issues
    }
}

data class ValidationIssue(
    val severity: Severity, val location: String, val message: String
)

enum class Severity {
    WARNING, ERROR
}