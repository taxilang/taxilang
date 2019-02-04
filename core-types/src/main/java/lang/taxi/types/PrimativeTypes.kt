package lang.taxi.types


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
    ARRAY("Array"),
    ANY("Any"),
    DOUBLE("Double"),
    VOID("Void");

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

//        fun fromToken(typeToken: TaxiParser.TypeTypeContext): PrimitiveType {
//            return fromDeclaration(typeToken.primitiveType()!!.text)
//        }

        fun isPrimitiveType(qualifiedName: String): Boolean {
            return typesByLookup.containsKey(qualifiedName)
        }
    }
}
