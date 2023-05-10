package lang.taxi

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.compiler.SymbolKind
import lang.taxi.functions.Function
import lang.taxi.functions.stdlib.stdLibName
import lang.taxi.services.ConsumedOperation
import lang.taxi.services.ServiceDefinition
import lang.taxi.stdlib.StdLibSchema
import lang.taxi.types.*
import lang.taxi.utils.toEither
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class TypeSystem(importedTokens: List<ImportableToken>) : TypeProvider {

   private val importedTokenMap: Map<String, ImportableToken> = importedTokens.associateBy { it.qualifiedName }
   private val compiledTokens = mutableMapOf<String, ImportableToken>()
   private val referencesToUnresolvedTypes = mutableMapOf<String, Token>()
   private val serviceDefinitionMap = mutableMapOf<String, ServiceDefinition>()

   fun tokenList(includeImportedTypes: Boolean = false): List<ImportableToken> {
      val importedTypes = if (includeImportedTypes) importedTokenMap.values.toList()
      else emptyList()
      return compiledTokens.values.toList() + importedTypes
   }

   fun typeList(includeImportedTypes: Boolean = false): List<Type> {
      return tokenList(includeImportedTypes).filterIsInstance<Type>()
   }

   fun getOrCreate(typeName: String, location: Token, symbolKind: SymbolKind = SymbolKind.TYPE): ImportableToken {
      val type = getOrCreate(typeName, symbolKind)
      if (type is ObjectType && !type.isDefined) {
         referencesToUnresolvedTypes.put(typeName, location)
      }
      return type
   }

   fun getOrCreate(typeName: String, symbolKind: SymbolKind = SymbolKind.TYPE): ImportableToken {
      if (PrimitiveType.isPrimitiveType(typeName)) {
         return PrimitiveType.fromDeclaration(typeName)
      }
      if (isImported(typeName, symbolKind)) {
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

   fun contains(qualifiedName: String, symbolKind: SymbolKind = SymbolKind.TYPE): Boolean {
      return isImported(qualifiedName, symbolKind) || containsCompiledToken(
         qualifiedName,
         symbolKind
      ) || PrimitiveType.isPrimitiveType(qualifiedName)
   }

   private fun containsCompiledToken(qualifiedName: String, symbolKind: SymbolKind): Boolean {
      return compiledTokens.containsKey(qualifiedName)
         && symbolKind.matches(compiledTokens.getValue(qualifiedName))
   }

   private fun isImported(qualifiedName: String, symbolKind: SymbolKind): Boolean {
      return importedTokenMap.containsKey(qualifiedName)
         && symbolKind.matches(importedTokenMap.getValue(qualifiedName))
   }

   fun isDefined(qualifiedName: String): Boolean {
      if (!contains(qualifiedName)) return false
      compiledTokens[qualifiedName]?.let { importableToken ->
         return when (importableToken) {
            is DefinableToken<*> -> {
               val registeredType = importableToken as DefinableToken<*>
               registeredType.definition != null
            }

            else -> error("unhandled token type ${importableToken::class.simpleName}")
         }
      }
      return false
   }

   /**
    * Registers the token with the typesystem.
    * The token is returned for convenient chaining
    */
   fun <TDef : TokenDefinition, TToken : DefinableToken<TDef>> register(
      type: TToken,
      overwrite: Boolean = false
   ): TToken {
      if (compiledTokens.containsKey(type.qualifiedName)) {
         val registeredType = compiledTokens[type.qualifiedName]!! as DefinableToken<TDef>
         if (registeredType.definition != null && type.definition != null && registeredType.definition != type.definition && !overwrite) {
            throw IllegalArgumentException("Attempting to redefine type ${type.qualifiedName}")
         }
         registeredType.definition = type.definition
         if (registeredType is UserType<*, *> && type is UserType<*, *>) {
            // Nasty for-each stuff here because of generic oddness
            (type as UserType<TypeDefinition, TypeDefinition>).extensions.forEach {
               (registeredType as UserType<TypeDefinition, TypeDefinition>).addExtension(it)
            }
         }
      } else {
         compiledTokens[type.qualifiedName] = type
      }
      return type
   }

   fun getTokens(
      includeImportedTypes: Boolean = false,
      predicate: (ImportableToken) -> Boolean
   ): List<ImportableToken> {
      return this.tokenList(includeImportedTypes).filter(predicate).toList()
   }

   fun getTokenOrError(
      qualifiedName: String,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE
   ): Either<CompilationError, ImportableToken> {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return PrimitiveType.fromDeclaration(qualifiedName).right()
      }

      if (isImported(qualifiedName, symbolKind)) {
         return getImportedToken(qualifiedName).right()
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

   fun getFunction(qualifiedName: String): Function {
      return getToken(qualifiedName) as Function
   }

   fun getOperationOrError(
      qualifiedName: String,
      context: ParserRuleContext
   ): Either<CompilationError, ConsumedOperation> {
      return try {
         val serviceQualifiedName = qualifiedName.split(".").dropLast(1).joinToString(".")
         val operationName = qualifiedName.split(".").last()
         val matchedServiceDefinition = this.serviceDefinitionMap[serviceQualifiedName]
         return matchedServiceDefinition
            ?.operations
            ?.firstOrNull { opName -> opName == operationName }
            ?.let { ConsumedOperation(serviceQualifiedName, it) }
            ?.right() ?: CompilationError(
            context.start,
            "$qualifiedName is not defined",
            context.source().normalizedSourceName
         )
            .left()
      } catch (e: Exception) {
         CompilationError(context.start, "$qualifiedName is not defined", context.source().normalizedSourceName)
            .left()
      }
   }

   override fun getType(qualifiedName: String): Type {
      return getToken(qualifiedName) as Type
   }

   private fun getToken(qualifiedName: String, symbolKind: SymbolKind = SymbolKind.TYPE): ImportableToken {
      return getTokenIfPresent(qualifiedName, symbolKind)
         ?: throw IllegalArgumentException("$qualifiedName is not defined")
   }

   private fun getTokenIfPresent(qualifiedName: String, symbolKind: SymbolKind = SymbolKind.TYPE): ImportableToken? {
      if (PrimitiveType.isPrimitiveType(qualifiedName)) {
         return PrimitiveType.fromDeclaration(qualifiedName)
      }


      if (isImported(qualifiedName, symbolKind)) {
         // TODO Handle case where SymbolKind is an annotation
         return if (symbolKind == SymbolKind.FUNCTION) {
            getImportedFunction(qualifiedName)
         } else {
            return getImportedType(qualifiedName)
         }
      }

      if (this.compiledTokens.containsKey(qualifiedName) && symbolKind.matches(
            this.compiledTokens.getValue(
               qualifiedName
            )
         )
      ) {
         return this.compiledTokens[qualifiedName]
      }

      return null
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

   // THe whole additionalImports thing is for when we're
   // accessing prior to compiling (ie., in the language server).
   // During normal compilation, don't need to pass anything
   fun qualify(
      namespace: Namespace,
      name: String,
      explicitImports: List<QualifiedName> = emptyList(),
      symbolKind: SymbolKind = SymbolKind.TYPE
   ): String {
      if (name.contains(".")) {
         // This is already qualified
         return name
      }
      if (PrimitiveType.isPrimitiveType(name)) {
         return PrimitiveType.fromDeclaration(name).qualifiedName
      }

      // If the type was explicitly imported, use that
      val matchedExplicitImport = explicitImports.filter { it.typeName == name }
         .distinct()
         .filter { importedName ->
            getTokenIfPresent(importedName.fullyQualifiedName, symbolKind) != null
         }
      if (matchedExplicitImport.size == 1) {
         return matchedExplicitImport.first().toString()
      }

      // If the type wasn't explicitly imported, but exists in the same namespace, then resolve to that.
      val matchesInNamespace = this.getTokens(includeImportedTypes = true) { token ->
         val qualifiedName = token.toQualifiedName()
         val matchesOnName =
            qualifiedName.namespace == namespace && qualifiedName.typeName == name && symbolKind.matches(token)
         matchesOnName
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
      val matchedImplicitImports =
         (importedTokenMap + compiledTokens).values.map { it.toQualifiedName() }.filter { it.typeName == name }
      if (matchedImplicitImports.size == 1) {
         return matchedImplicitImports.first().toString()
      } else if (matchedImplicitImports.size > 1) {
         val possibleReferences = matchedImplicitImports.joinToString { it.toString() }
         throw AmbiguousNameException("Name reference $name is ambiguous, and could refer to any of the available types $possibleReferences")
      }

      // Check to see if it's present in the stdlib
      if (StdLibSchema.taxiDocument.containsImportable(stdLibName(name).fullyQualifiedName)) {
         return stdLibName(name).fullyQualifiedName
      }

      if (namespace.isEmpty()) {
         return name
      }
      return "$namespace.$name"
   }

   fun registerServiceDefinitions(serviceDefinitions: List<ServiceDefinition>) {
      serviceDefinitions.forEach { serviceDefinition ->
         serviceDefinitionMap[serviceDefinition.qualifiedName] = serviceDefinition
      }
   }

}

class AmbiguousNameException(message: String) : RuntimeException(message)
