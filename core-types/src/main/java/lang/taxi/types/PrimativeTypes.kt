package lang.taxi.types


enum class VoidType : Type {
    VOID;

    override val qualifiedName: String = "lang.taxi.Void"
    override val compilationUnits: List<CompilationUnit> = listOf(CompilationUnit.ofSource(SourceCode("Built in", "// Built-in type")))
}

enum class PrimitiveType(val declaration: String, val typeDoc:String) : Type {
    BOOLEAN("Boolean", "Represents a value which is either `true` or `false`."),
    STRING("String", "A collection of characters."),
    INTEGER("Int", "A signed integer - ie. a whole number (positive or negative), with no decimal places"),
    DECIMAL("Decimal", "A signed decimal number - ie., a whole number with decimal places."),
    LOCAL_DATE("Date", "A date, without a time or timezone."),
    TIME("Time", "Time only, excluding the date part"),
    DATE_TIME("DateTime", "A date and time, without a timezone.  Generally, favour using Instant which represents a point-in-time, as it has a timezone attached"),
    INSTANT("Instant", "A point in time, with date, time and timezone.  Follows ISO standard convention of YYYY-mm-yyThh:dd:ssZ"),
    ARRAY("Array", "A collection of things"),
    ANY("Any", "Can be anything.  Try to avoid using 'Any' as it's not descriptive - favour using a strongly typed approach instead"),
    DOUBLE("Double", "Represents a double-precision 64-bit IEEE 754 floating point number."),
    VOID("Void", "Nothing.  Represents the return value of operations that don't return anything.");

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
