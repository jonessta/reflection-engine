package au.clef.api

import au.clef.engine.registry.MethodSourceTypes
import kotlin.test.*

class DefaultClassResolverTest {

    @Test
    fun resolve_returnsStructuredType_forKnownQualifiedClassName() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(Person1::class.java)
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val resolved: ResolvedType = resolver.resolve(Person1::class.java.name)

        val structured: ResolvedType.Structured = assertIs(resolved)
        assertEquals(Person1::class.java, structured.type)
    }

    @Test
    fun resolve_returnsStructuredType_forUniqueSimpleName() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(Person1::class.java)
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val resolved: ResolvedType = resolver.resolve("Person1")

        val structured: ResolvedType.Structured = assertIs(resolved)
        assertEquals(Person1::class.java, structured.type)
    }

    @Test
    fun resolve_returnsScalarType_forKnownScalarQualifiedName() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(String::class.java)
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val resolved: ResolvedType = resolver.resolve(String::class.java.name)

        val scalar: ResolvedType.Scalar = assertIs(resolved)
        assertEquals(String::class.java, scalar.type)
    }

    @Test
    fun resolve_returnsScalarType_forKnownScalarSimpleName() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(String::class.java)
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val resolved: ResolvedType = resolver.resolve("String")

        val scalar: ResolvedType.Scalar = assertIs(resolved)
        assertEquals(String::class.java, scalar.type)
    }

    @Test
    fun resolve_prefersQualifiedNames_evenWhenSimpleNamesAreAmbiguous() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(
                    au.clef.api.alpha.Duplicate::class.java,
                    au.clef.api.beta.Duplicate::class.java
                )
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val alpha: ResolvedType = resolver.resolve(au.clef.api.alpha.Duplicate::class.java.name)
        val beta: ResolvedType = resolver.resolve(au.clef.api.beta.Duplicate::class.java.name)

        assertEquals(au.clef.api.alpha.Duplicate::class.java, assertIs<ResolvedType.Structured>(alpha).type)
        assertEquals(au.clef.api.beta.Duplicate::class.java, assertIs<ResolvedType.Structured>(beta).type)
    }

    @Test
    fun resolve_rejectsAmbiguousSimpleName() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(
                    au.clef.api.alpha.Duplicate::class.java,
                    au.clef.api.beta.Duplicate::class.java
                )
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val ex: IllegalArgumentException = assertFailsWith {
            resolver.resolve("Duplicate")
        }

        assertTrue(ex.message!!.contains("Unknown type: Duplicate"))
    }

    @Test
    fun resolve_rejectsUnknownType() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(Person1::class.java)
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val ex: IllegalArgumentException = assertFailsWith {
            resolver.resolve("MissingType")
        }

        assertTrue(ex.message!!.contains("Unknown type: MissingType"))
    }

    @Test
    fun resolve_handlesMixedScalarAndStructuredKnownClasses() {
        val resolver: DefaultClassResolver = DefaultClassResolver(
            methodSourceTypes = FakeMethodSourceTypes(
                knownClasses = listOf(
                    String::class.java,
                    Person1::class.java,
                    Int::class.javaObjectType
                )
            ),
            scalarRegistry = ScalarTypeRegistry()
        )

        val stringResolved: ResolvedType = resolver.resolve("String")
        val intResolved: ResolvedType = resolver.resolve(Int::class.javaObjectType.name)
        val personResolved: ResolvedType = resolver.resolve("Person1")

        assertIs<ResolvedType.Scalar>(stringResolved)
        assertIs<ResolvedType.Scalar>(intResolved)
        assertIs<ResolvedType.Structured>(personResolved)
    }
}

private data class FakeMethodSourceTypes(
    override val declaringClasses: List<Class<*>> = emptyList(),
    override val knownClasses: List<Class<*>>
) : MethodSourceTypes

private class Person1
