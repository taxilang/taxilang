package lang.taxi

import lang.taxi.TaxiParser.ServiceDeclarationContext
import lang.taxi.compiler.SymbolKind
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File

internal typealias Namespace = String

data class Tokens(
   val imports: List<Pair<String, TaxiParser.ImportDeclarationContext>>,
   val unparsedTypes: Map<String, Pair<Namespace, ParserRuleContext>>,
   val unparsedExtensions: List<Pair<Namespace, ParserRuleContext>>,
   val unparsedServices: Map<String, Pair<Namespace, ServiceDeclarationContext>>,
   val unparsedPolicies: Map<String, Pair<Namespace, TaxiParser.PolicyDeclarationContext>>,
   val unparsedFunctions: Map<String, Pair<Namespace, TaxiParser.FunctionDeclarationContext>>,
   val namedQueries: List<Pair<Namespace, TaxiParser.NamedQueryContext>>,
   val anonymousQueries: List<Pair<Namespace, TaxiParser.AnonymousQueryContext>>,
   val tokenStore: TokenStore
) {
   companion object {
      /**
       * Combines multiple sets of tokens to a single Tokens instance.
       * Replaces a legacy reduce() function, which was very non-performant.
       *
       */
      fun combine(members: List<Tokens>): Tokens {
         val imports: MutableList<Pair<String, TaxiParser.ImportDeclarationContext>> = mutableListOf()
         val unparsedTypes: MutableMap<String, Pair<Namespace, ParserRuleContext>> = mutableMapOf()
         val unparsedExtensions: MutableList<Pair<Namespace, ParserRuleContext>> = mutableListOf()
         val unparsedServices: MutableMap<String, Pair<Namespace, ServiceDeclarationContext>> = mutableMapOf()
         val unparsedPolicies: MutableMap<String, Pair<Namespace, TaxiParser.PolicyDeclarationContext>> = mutableMapOf()
         val unparsedFunctions: MutableMap<String, Pair<Namespace, TaxiParser.FunctionDeclarationContext>> =
            mutableMapOf()
         val namedQueries: MutableList<Pair<Namespace, TaxiParser.NamedQueryContext>> = mutableListOf()
         val anonymousQueries: MutableList<Pair<Namespace, TaxiParser.AnonymousQueryContext>> = mutableListOf()

         members.forEach { tokens ->
            imports.addAll(tokens.imports)
            unparsedTypes.putAll(tokens.unparsedTypes)
            unparsedExtensions.addAll(tokens.unparsedExtensions)
            unparsedServices.putAll(tokens.unparsedServices)
            unparsedPolicies.putAll(tokens.unparsedPolicies)
            unparsedFunctions.putAll(tokens.unparsedFunctions)
            namedQueries.addAll(tokens.namedQueries)
            anonymousQueries.addAll(tokens.anonymousQueries)
         }
         val tokenStores = members.map { it.tokenStore }
         val tokenStore = TokenStore.combine(tokenStores)

         return Tokens(
            imports,
            unparsedTypes,
            unparsedExtensions,
            unparsedServices,
            unparsedPolicies,
            unparsedFunctions,
            namedQueries,
            anonymousQueries,
            tokenStore
         )
      }
   }

   val unparsedTypeNames: Set<QualifiedName> by lazy {
      unparsedTypes.keys.map { QualifiedName.from(it) }.toSet()
   }

   private val typeNamesBySource: Map<String, List<QualifiedName>> by lazy {
      unparsedTypes.map { (name, namespaceContextPair) ->
         val (_, context) = namespaceContextPair
         context.source().normalizedSourceName to QualifiedName.from(name)
      }.groupBy { it.first }
         .mapValues { (_, value) -> value.map { it.second } }
   }
   private val importsBySourceName: Map<String, List<Pair<String, TaxiParser.ImportDeclarationContext>>> by lazy {
      imports.groupBy { it.second.source().normalizedSourceName }
   }



   /**
    * This method is currently stubbed out.
    * There's a problem with the existing implementation that it incorrectly rejects
    * types that are semantically equivalent.  We need to permit this, in order to let
    * two microservices declare the same definition of a type, without requiring them to
    * adopt a shared library.
    * However, the original implementation of this worked, but then broke when we introduced imports.
    * Then we provided an implementation that was too strict, and just rejected all redefinition of types,
    * even if they have the same underlying definition.
    *
    * For now, this is disabled, but we need to resolve this.
    */
   private fun collectDuplicateTypes(others: Tokens): List<CompilationError> {
      // TODO : This used to be called as part of a reduce function, that we've now removed.
      // This menas that checking for ducpliate types (which was disabled anyway)
      // now isn't called anymore.

      // Stubbed for a demo
      // TODO("Revisit duplicate type definition handling")
      return emptyList()
      // Don't allow definition of given types in multiple files.
      // Though this is a bit too strict (we'd like to allow multiple definitions that are semantically equivelant to each other)
      // this is a quick update to resolve the immediate issue at client side.
      val duplicateTypeNames = this.unparsedTypes.keys.filter { others.unparsedTypes.containsKey(it) }
      val errors = if (duplicateTypeNames.isNotEmpty()) {
         val compilationErrors = duplicateTypeNames.map {
            CompilationError(
               (others.unparsedTypes[it]
                  ?: error("")).second.start,
               "Attempt to redefine type $it. Types may be extended (using an extension), but not redefined"
            )
         }
         compilationErrors
      } else emptyList()
      return errors
   }

   private fun collectDuplicateServices(others: Tokens): List<CompilationError> {
      val duplicateServices = this.unparsedServices.keys.filter { others.unparsedServices.containsKey(it) }
      val errors = duplicateServices.map {
         CompilationError(
            others.unparsedServices[it]!!.second.start,
            "Attempt to redefine service $it. Services may be extended (using an extension), but not redefined"
         )
      }
      return errors
   }

   fun importTokensInSource(sourceName: String): List<Pair<QualifiedName, TaxiParser.ImportDeclarationContext>> {
      return importsBySourceName.getOrDefault(SourceNames.normalize(sourceName), emptyList())
         .map { (name, token) -> QualifiedName.from(name) to token }
   }

   fun importedTypeNamesInSource(sourceName: String): List<QualifiedName> {
      return importTokensInSource(sourceName).map { it.first }
   }

   fun typeNamesForSource(sourceName: String): List<QualifiedName> {
      val normalized = SourceNames.normalize(sourceName)
      return typeNamesBySource.getOrElse(normalized) {
         // The sourceName wasn't found in the cache of tokens.
         // This can happen typically if the file is empty, or failed to compile
         // and we weren't able to get any tokens.
         if (this.tokenStore.containsTokensForSource(sourceName)) {
            // The compiler knows about the sourceName, but there weren't
            // any types in it.  That's valid.
            return@getOrElse emptyList()
         }

         // However, more recently this happens if the file names aren't normalized
         // consistently.
         // So, adding this bomb here.
         // If this hasn't gone off in a while, we can probably delete it.
         val name = sourceName.split(File.separator).last()
         if (typeNamesBySource.keys.any { it.endsWith(name) }) {
            val matches = typeNamesBySource.keys.filter { it.endsWith(name) }.joinToString(",")
            error("Looks a lot like file uri's aren't getting normalized properly. Looking for $normalized, found nothing, but $matches was")
         }
         emptyList()
      }
   }

   fun hasUnparsedImportableToken(qualifiedName: String): Boolean {
      return this.unparsedTypes.containsKey(qualifiedName) || this.unparsedFunctions.containsKey(qualifiedName)
   }

   fun containsUnparsedType(qualifiedTypeName: String, symbolKind: SymbolKind): Boolean {
      return if (this.unparsedTypes.containsKey(qualifiedTypeName)) {
         val (_, unparsedToken) = this.unparsedTypes.getValue(qualifiedTypeName)
         symbolKind.matches(unparsedToken)
      } else {
         false
      }
   }

   fun containsUnparsedService(qualifiedName: String) : Boolean {
      return this.unparsedServices.containsKey(qualifiedName)
   }

}


class TokenCollator : TaxiBaseListener() {
   val exceptions = mutableMapOf<ParserRuleContext, Exception>()
   private var namespace: String = Namespaces.DEFAULT_NAMESPACE
   private var imports = mutableListOf<Pair<String, TaxiParser.ImportDeclarationContext>>()

   private val unparsedTypes = mutableMapOf<String, Pair<Namespace, ParserRuleContext>>()
   private val unparsedExtensions = mutableListOf<Pair<Namespace, ParserRuleContext>>()
   private val unparsedServices = mutableMapOf<String, Pair<Namespace, ServiceDeclarationContext>>()
   private val unparsedPolicies = mutableMapOf<String, Pair<Namespace, TaxiParser.PolicyDeclarationContext>>()
   private val unparsedFunctions = mutableMapOf<String, Pair<Namespace, TaxiParser.FunctionDeclarationContext>>()
   private val namedQueries = mutableListOf<Pair<Namespace, TaxiParser.NamedQueryContext>>()
   private val anonymousQueries = mutableListOf<Pair<Namespace, TaxiParser.AnonymousQueryContext>>()


   //    private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
//    private val unparsedExtensions = mutableListOf<ParserRuleContext>()
//    private val unparsedServices = mutableMapOf<String, ServiceDeclarationContext>()
   private val tokenStore = TokenStore()
   fun tokens(): Tokens {
      return Tokens(
         imports,
         unparsedTypes,
         unparsedExtensions,
         unparsedServices,
         unparsedPolicies,
         unparsedFunctions,
         namedQueries,
         anonymousQueries,
         tokenStore
      )
   }


   override fun exitEveryRule(ctx: ParserRuleContext) {
      val zeroBasedLineNumber = ctx.start.line - 1

      // The source can be unknown if we created a fake token
      // during error recovery
      val sourceName = ctx.start.tokenSource?.sourceName?.let {         SourceNames.normalize(it)      } ?: "UnknownSource"
      tokenStore.insert(sourceName, zeroBasedLineNumber, ctx.start.charPositionInLine, ctx)
   }

   override fun exitImportDeclaration(ctx: TaxiParser.ImportDeclarationContext) {
      if (collateExceptions(ctx)) {
         imports.add(ctx.qualifiedName().identifier().text() to ctx)
      }
      super.exitImportDeclaration(ctx)
   }


   override fun exitFieldDeclaration(ctx: TaxiParser.FieldDeclarationContext) {
      collateExceptions(ctx)
      // Check to see if an inline type alias is declared
      // If so, mark it for processing later
      val typeType = ctx.fieldTypeDeclaration()
      if (typeType?.aliasedType() != null) {
         val classOrInterfaceType = typeType.nullableTypeReference().typeReference().qualifiedName()
         unparsedTypes.put(qualify(classOrInterfaceType.identifier().text()), namespace to typeType)
      }
      super.exitFieldDeclaration(ctx)
   }

   override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
      if (collateExceptions(ctx)) {
         val name = qualify(ctx.qualifiedName().identifier().text())
         unparsedTypes.put(name, namespace to ctx)
      }
      super.exitEnumDeclaration(ctx)
   }

   override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
      collateExceptions(ctx)
      ctx.qualifiedName()?.identifier()?.text()?.let { namespace -> this.namespace = namespace }
      super.exitNamespaceDeclaration(ctx)
   }

   override fun enterNamespaceBody(ctx: TaxiParser.NamespaceBodyContext) {
      val parent = ctx.parent as ParserRuleContext
      val namespaceNode = parent.getChild(TaxiParser.QualifiedNameContext::class.java, 0)
      this.namespace = namespaceNode.identifier().text()
      super.enterNamespaceBody(ctx)
   }

   override fun exitPolicyDeclaration(ctx: TaxiParser.PolicyDeclarationContext) {
      if (collateExceptions(ctx)) {
         // TODO : Why did I have to change this?  Why is Identifier() retuning null now?
         // Was:  qualify(ctx.policyIdentifier().identifier().text)
         val qualifiedName = qualify(ctx.policyIdentifier().text)
         unparsedPolicies[qualifiedName] = namespace to ctx
      }
      super.exitPolicyDeclaration(ctx)
   }

   override fun exitServiceDeclaration(ctx: ServiceDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.identifier().text)
         unparsedServices[qualifiedName] = namespace to ctx
      }
      super.exitServiceDeclaration(ctx)
   }

   override fun exitFunctionDeclaration(ctx: TaxiParser.FunctionDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.qualifiedName().identifier().text())
         unparsedFunctions[qualifiedName] = namespace to ctx
      }
   }

   override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes[typeName] = namespace to ctx
      }
      super.exitTypeDeclaration(ctx)
   }

   override fun exitTypeAliasDeclaration(ctx: TaxiParser.TypeAliasDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes.put(typeName, namespace to ctx)
      }
      super.exitTypeAliasDeclaration(ctx)
   }

   override fun exitTypeExtensionDeclaration(ctx: TaxiParser.TypeExtensionDeclarationContext) {
      collateExceptions(ctx)
      unparsedExtensions.add(namespace to ctx)
      super.exitTypeExtensionDeclaration(ctx)
   }

   override fun exitTypeAliasExtensionDeclaration(ctx: TaxiParser.TypeAliasExtensionDeclarationContext) {
      collateExceptions(ctx)
      unparsedExtensions.add(namespace to ctx)
      super.exitTypeAliasExtensionDeclaration(ctx)
   }

   override fun exitEnumExtensionDeclaration(ctx: TaxiParser.EnumExtensionDeclarationContext) {
      collateExceptions(ctx)
      unparsedExtensions.add(namespace to ctx)
      super.exitEnumExtensionDeclaration(ctx)
   }

   override fun exitAnnotationTypeDeclaration(ctx: TaxiParser.AnnotationTypeDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes[typeName] = namespace to ctx
      }
      super.exitAnnotationTypeDeclaration(ctx)
   }

   override fun exitNamedQuery(ctx: TaxiParser.NamedQueryContext) {
      namedQueries.add(namespace to ctx)
   }

   override fun exitAnonymousQuery(ctx: TaxiParser.AnonymousQueryContext) {
      anonymousQueries.add(namespace to ctx)
   }

   /**
    * Returns true if the context was valid - ie., no exception was detected.
    * Returns false if an exception was present, and it's potentially unsafe to process
    * this context node
    */
   private fun collateExceptions(ctx: ParserRuleContext): Boolean {
      if (ctx.exception != null) {
         exceptions.put(ctx, ctx.exception)
         return false
      }
      return true
   }

   private fun qualify(name: String): String {
      if (name.contains("."))
      // This is already qualified
         return name
      if (namespace.isEmpty()) return name
      return "$namespace.$name"
   }
}

fun List<TerminalNode>.text(): String {
   return this.joinToString(".")
}
