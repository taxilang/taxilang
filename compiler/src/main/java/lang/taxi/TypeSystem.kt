package lang.taxi

import arrow.core.Either
import lang.taxi.functions.Function
import lang.taxi.types.*
import lang.taxi.utils.toEither
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class TypeSystem(importedTokens: List<ImportableToken>) : TypeProvider {

   private val importedTokenMap: Map<String, ImportableToken> = importedTokens.associateBy { it.qualifiedName }
   private val compiledTokens = mutableMapOf<String, ImportableToken>()
   private val referencesToUnresolvedTypes = mutableMapOf<String, Token>()

   fun tokenList(includeImportedTypes: Boolean = false): List<ImportableToken> {
      val importedTypes = if (includeImportedTypes) importedTokenMap.values.toList()
      else emptyList()
      return compiledTokens.values.toList() + importedTypes
   }

   fun typeList(includeImportedTypes: Boolean = false): List<Type> {
      return tokenList(includeImportedTypes).filterIsInstance<Type>()
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

      return compiledTokens.getOrPut(typeName, { ObjectType.undefined(typeName) }) as Type
   }

   private fun getImportedToken(name: String): ImportableToken {
      return importedTokenMap[name] ?: error("Token $name not imported")
   }

   private fun getImportedType(typeName: String): Type {
      return getImportedToken(typeName) as Type
   }

   private fun getImportedFunction(name: String): Function {
      return getImportedToken(name) as Function
   }

   fun contains(qualifiedName: String): Boolean {
      return isImported(qualifiedName) || compiledTokens.containsKey(qualifiedName)
   }

   private fun isImported(qualifiedName: String): Boolean {
      return importedTokenMap.containsKey(qualifiedName)
   }

   fun isDefined(qualifiedName: String): Boolean {
      if (!contains(qualifiedName)) return false
      compiledTokens[qualifiedName]?.let { importableToken ->
         return when (importableToken) {
            is Function -> true
            is UserType<*, *> -> {
               val registeredType = importableToken as UserType<TypeDefinition, TypeDefinition>
               registeredType.definition != null
            }
            else -> error("unhandled token type ${importableToken::class.simpleName}")
         }
      }
      return false
   }

   fun <TDef : TokenDefinition> register(type: DefinableToken<TDef>) {
      if (compiledTokens.containsKey(type.qualifiedName)) {
         val registeredType = compiledTokens[type.qualifiedName]!! as DefinableToken<TDef>
         if (registeredType.definition != null && type.definition != null && registeredType.definition != type.definition) {
            throw IllegalArgumentException("Attempting to redefine type ${type.qualifiedName}")
         }
         if (registeredType.definition != type.definition) {
            registeredType.definition = type.definition
            if (registeredType is UserType<*, *> && type is UserType<*, *>) {
               // Nasty for-each stuff here because of generic oddness
               (type as UserType<TypeDefinition, TypeDefinition>).extensions.forEach {
                  (registeredType as UserType<TypeDefinition, TypeDefinition>).addExtension(it)
               }
            }
         }
      } else {
         compiledTokens[type.qualifiedName] = type
      }
   }

   fun getTokens(includeImportedTypes: Boolean = false, predicate: (ImportableToken) -> Boolean): List<ImportableToken> {
      return this.tokenList(includeImportedTypes).filter(predicate).toList()
   }

   fun getTokenOrError(qualifiedName: String, context: ParserRuleContext): Either<CompilationError, ImportableToken> {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return Either.right(PrimitiveType.fromDeclaration(qualifiedName))
      }

      if (isImported(qualifiedName)) {
         return Either.right(getImportedType(qualifiedName))
      }


      return this.compiledTokens[qualifiedName].toEither {
         CompilationError(context.start, "$qualifiedName is not defined", context.source().normalizedSourceName)
      }
   }

   fun getTypeOrError(qualifiedName: String, context: ParserRuleContext): Either<CompilationError, Type> {
      return getTokenOrError(qualifiedName, context).map { it as Type }
   }

   fun getFunctionOrError(qualifiedName: String, context: ParserRuleContext): Either<CompilationError, Function> {
      return getTokenOrError(qualifiedName, context).map { it as Function }
   }

   fun getFunction(qualifiedName: String):Function {
      return getToken(qualifiedName) as Function
   }

   override fun getType(qualifiedName: String): Type {
      return getToken(qualifiedName) as Type
   }

   private fun getToken(qualifiedName: String):ImportableToken {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return PrimitiveType.fromDeclaration(qualifiedName)
      }

      if (isImported(qualifiedName)) {
         return getImportedType(qualifiedName)
      }

      return this.compiledTokens[qualifiedName] as ImportableToken?
         ?: throw IllegalArgumentException("$qualifiedName is not defined as a type")
   }

   fun containsUnresolvedTypes(): Boolean {
      val unresolved = unresolvedTypes()
      return unresolved.isNotEmpty()
   }

   private fun unresolvedTypes(): Set<String> {
      return compiledTokens.values
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
   fun qualify(namespace: Namespace, name: String, explicitImports: List<QualifiedName> = emptyList()): String {
      if (name.contains(".")) {
         // This is already qualified
         return name
      }

      // If the type was explicitly imported, use that
      val matchedExplicitImport = explicitImports.filter { it.typeName == name }
         .distinct()
      if (matchedExplicitImport.size == 1) {
         return matchedExplicitImport.first().toString()
      }

      // If the type wasn't explicitly imported, but exists in the same namespace, then resolve to that.
      val matchesInNamespace = this.getTokens(includeImportedTypes = true) { type ->
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
      val matchedImplicitImports = importedTokenMap.values.map { it.toQualifiedName() }.filter { it.typeName == name }
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
