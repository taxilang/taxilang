package lang.taxi

import arrow.core.Either
import lang.taxi.types.*
import lang.taxi.utils.toEither
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class TypeSystem(importedTypes: List<Type>) : TypeProvider {

   private val importedTypeMap: Map<String, Type> = importedTypes.associateBy { it.qualifiedName }
   private val types = mutableMapOf<String, Type>()
   private val referencesToUnresolvedTypes = mutableMapOf<String, Token>()

   fun typeList(includeImportedTypes: Boolean = false): List<Type> {
      val importedTypes = if (includeImportedTypes) importedTypeMap.values.toList() else emptyList()
      return types.values.toList() + importedTypes
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
      if (isImported(typeName)) {
         return getImportedType(typeName)
      }

      return types.getOrPut(typeName, { ObjectType.undefined(typeName) })
   }

   private fun getImportedType(typeName: String): Type {
      return importedTypeMap[typeName] ?: error("Type $typeName not imported")
   }

   fun contains(qualifiedName: String): Boolean {
      return isImported(qualifiedName) || types.containsKey(qualifiedName)
   }

   private fun isImported(qualifiedName: String): Boolean {
      return importedTypeMap.containsKey(qualifiedName)
   }

   fun isDefined(qualifiedName: String): Boolean {
      if (!contains(qualifiedName)) return false;
      val registeredType = types[qualifiedName]!! as UserType<TypeDefinition, TypeDefinition>
      return registeredType.definition != null
   }

   fun register(type: UserType<*, *>) {
      if (types.containsKey(type.qualifiedName)) {
         val registeredType = types[type.qualifiedName]!! as UserType<TypeDefinition, TypeDefinition>
         if (registeredType.definition != null && type.definition != null && registeredType.definition != type.definition) {
            throw IllegalArgumentException("Attempting to redefine type ${type.qualifiedName}")
         }
         if (registeredType.definition != type.definition) {
            registeredType.definition = type.definition
            // Nasty for-each stuff here because of generic oddness
            type.extensions.forEach { registeredType.addExtension(it) }
         }
      } else {
         types.put(type.qualifiedName, type)
      }
   }

   fun getTypes(includeImportedTypes: Boolean = false, predicate: (Type) -> Boolean): List<Type> {
      return this.typeList(includeImportedTypes).filter(predicate).toList()
   }

   fun getTypeOrError(qualifiedName: String, context: ParserRuleContext): Either<CompilationError, Type> {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return Either.right(PrimitiveType.fromDeclaration(qualifiedName))
      }

      if (isImported(qualifiedName)) {
         return Either.right(getImportedType(qualifiedName))
      }


      return this.types[qualifiedName].toEither {
         CompilationError(context.start, "$qualifiedName is not defined as a type", context.source().normalizedSourceName)
      }

   }

   override fun getType(qualifiedName: String): Type {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return PrimitiveType.fromDeclaration(qualifiedName)
      }

      if (isImported(qualifiedName)) {
         return getImportedType(qualifiedName)
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


   // THe whole additionalImports thing is for when we're
   // accessing prior to compiling (ie., in the language server).
   // During normal compilation, don't need to pass anything
   fun qualify(namespace: Namespace, name: String, explicitImports:List<QualifiedName> = emptyList()): String {
      if (name.contains(".")) {
         // This is already qualified
         return name
      }

      // If the type was explicitly imported, use that
      val matchedExplicitImport = explicitImports.filter { it.typeName == name }
      if (matchedExplicitImport.size == 1) {
         return matchedExplicitImport.first().toString()
      }

      // If the type wasn't explicitly imported, but exists in the same namespace, then resolve to that.
      val matchesInNamespace = this.getTypes(includeImportedTypes = true) { type ->
         val qualifiedName = type.toQualifiedName()
         qualifiedName.namespace == namespace && qualifiedName.typeName == name
      }
      if (matchesInNamespace.size == 1) {
         return matchesInNamespace.first().qualifiedName
      }

      // If the type in unambiguous in other import sources, then use that.
      // Note : This is original behaviour, implemented to reduce the "import" noise.
      // (ie., help the user - if we can find the type, do so).
      // However, I'm not sure it makes good sense, as compilation in a file can break
      // just by adding another type with a similar name elsewhere.
      // Keeping it for now, but should decide if this is a bad idea.
      val matchedImplicitImports = importedTypeMap.values.map { it.toQualifiedName() }.filter { it.typeName == name }
      if (matchedImplicitImports.size == 1) {
         return matchedImplicitImports.first().toString()
      } else if (matchedImplicitImports.size > 1) {
         val possibleReferences = matchedImplicitImports.joinToString { it.toString() }
         throw AmbiguousNameException("Name reference $name is ambiguous, and could refer to any of the available types $possibleReferences")
      }

      if (namespace.isEmpty()) {
         return name
      }
      return "$namespace.$name"
   }

}

class AmbiguousNameException(message: String) : RuntimeException(message)
