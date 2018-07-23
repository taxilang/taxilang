package lang.taxi

import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import org.antlr.v4.runtime.Token
import java.lang.IllegalArgumentException

internal data class TypeProxy(override val qualifiedName: String, private val typeSystem: TypeSystem) : Type {
    fun isResolved(): Boolean = typeSystem.contains(this.qualifiedName)
    val resolvedType: ObjectType
        get() {
            assertResolved()
            return typeSystem.getType(this.qualifiedName) as ObjectType
        }

    private fun assertResolved() {
        if (!isResolved()) {
            throw IllegalAccessError("Can't read values of a proxy type until it's resolved")
        }
    }

    override val compilationUnits = listOf(CompilationUnit.unspecified())
}

class TypeSystem {

    private val types = mutableMapOf<String, Type>()
    private val referencesToUnresolvedTypes = mutableMapOf<String, Token>()

    fun typeList(): List<Type> {
        return types.values.toList()
    }

    fun getOrCreate(typeName: String, location: Token): Type {
        val type = getOrCreate(typeName)
        if (type is ObjectType && !type.isDefined) {
            referencesToUnresolvedTypes.put(typeName, location)
        }
        return type
    }

    fun getOrCreate(typeName: String): Type {
        if (PrimitiveType.isPrimitiveType(typeName)) {
            return PrimitiveType.fromDeclaration(typeName)
        }
        return types.getOrPut(typeName, { ObjectType.undefined(typeName) })
    }

    fun contains(qualifiedName: String): Boolean {
        return types.containsKey(qualifiedName)
    }

    fun isDefined(qualifiedName: String): Boolean {
        if (!contains(qualifiedName)) return false;
        val registeredType = types[qualifiedName]!! as UserType<TypeDefinition, TypeDefinition>
        return registeredType.definition != null
    }

    fun register(type: UserType<*, *>) {
        if (types.containsKey(type.qualifiedName)) {
            val registeredType = types[type.qualifiedName]!! as UserType<TypeDefinition, TypeDefinition>
            if (registeredType.definition != null && type.definition != null) {
                throw IllegalArgumentException("Attempting to redefine type ${type.qualifiedName}")
            }
            registeredType.definition = type.definition
            // Nasty for-each stuff here because of generic oddness
            type.extensions.forEach { registeredType.extensions.add(it!!) }
        } else {
            types.put(type.qualifiedName, type)
        }

    }

    fun getType(qualifiedName: String): Type {
        if (PrimitiveType.isPrimitiveType(qualifiedName)) {
            return PrimitiveType.fromDeclaration(qualifiedName)
        }
        return this.types[qualifiedName] ?: throw IllegalArgumentException("$qualifiedName is not defined as a type")
    }

    fun containsUnresolvedTypes(): Boolean {
        val unresolved = unresolvedTypes()
        return unresolved.isNotEmpty()
    }

    private fun unresolvedTypes(): Set<String> {
        return types.values
                .filter { it is ObjectType && !it.isDefined }
                .map { it.qualifiedName }.toSet()
    }

    fun assertAllTypesResolved() {
        if (containsUnresolvedTypes()) {
            val errors = unresolvedTypes().map { typeName ->
                CompilationError(referencesToUnresolvedTypes[typeName]!!, ErrorMessages.unresolvedType(typeName))
            }
            throw CompilationException(errors)
        }
    }

}
