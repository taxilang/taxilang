package lang.taxi.types

interface Type : Named, Compiled

interface TypeDefinition {
    val compilationUnit: CompilationUnit
}


/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type {
    var definition: TDef?

    val extensions: List<TExt>

    fun addExtension(extension: TExt): ErrorMessage?

    val isDefined: Boolean
        get() {
            return this.definition != null
        }

    override val compilationUnits: List<CompilationUnit>
        get() = (this.extensions.map { it.compilationUnit } + this.definition?.compilationUnit).filterNotNull()

    /**
     * A list of all the other types this UserType makes reference to.
     * Used when importing this type, to ensure the full catalogue of types is imported
     */
    val referencedTypes: List<Type>

}
