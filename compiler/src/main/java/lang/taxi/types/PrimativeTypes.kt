package lang.taxi.types

import lang.taxi.CompilationUnit
import lang.taxi.SourceCode
import lang.taxi.Type
import java.lang.IllegalArgumentException


enum class VoidType : Type {
    VOID;

    override val qualifiedName: String = "lang.taxi.Void"
    override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))
}

enum class PrimitiveType(val declaration: String) : Type {
    BOOLEAN("Boolean"),
    STRING("String"),
    INTEGER("Int"),
    DECIMAL("Decimal"),
    LOCAL_DATE("Date"),
    TIME("Time"),
    DATE_TIME("DateTime"),
    INSTANT("Instant"),
    DOUBLE("Double");

    override val qualifiedName: String
        get() = "lang.taxi.$declaration"

    override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))

    companion object {
        private val typesByName = values().associateBy { it.declaration }
        private val typesByQualifiedName = values().associateBy { it.qualifiedName }
        private val typesByLookup = typesByName + typesByQualifiedName

        fun fromDeclaration(value: String): PrimitiveType {
            return typesByLookup[value] ?: throw IllegalArgumentException("$value is not a valid primative")
        }

        fun isPrimitiveType(qualifiedName: String): Boolean {
            return typesByLookup.containsKey(qualifiedName)
        }
    }
}
