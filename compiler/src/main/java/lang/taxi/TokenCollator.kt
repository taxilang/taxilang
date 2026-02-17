package lang.taxi

import lang.taxi.TaxiParser.AnnotationTypeDeclarationContext
import lang.taxi.TaxiParser.ServiceDeclarationContext
import lang.taxi.TaxiParser.ToplevelObjectContext
import lang.taxi.TaxiParser.TypeDeclarationContext
import lang.taxi.compiler.SymbolKind
import lang.taxi.messages.Severity
import lang.taxi.types.CompilationUnit
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import lang.taxi.utils.takeHead
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File

internal typealias Namespace = String

data class Tokens(
   val imports: List<Pair<String, TaxiParser.ImportDeclarationContext>>,
   val unparsedTypes: List<Triple<String, Namespace, ParserRuleContext>>,
   // List of inline types: (QualifiedName of inline type, QualifiedName of declaring model)
   val unparsedInlineTypes: List<Pair<String, String>>,
   val unparsedExtensions: List<Pair<Namespace, ParserRuleContext>>,
   val unparsedServices: List<Triple<String, Namespace, ServiceDeclarationContext>>,
   val unparsedPolicies: List<Triple<String, Namespace, TaxiParser.PolicyDeclarationContext>>,
   val unparsedFunctions: List<Triple<String, Namespace, TaxiParser.FunctionDeclarationContext>>,
   val namedQueries: List<Triple<String, Namespace, TaxiParser.NamedQueryContext>>,
   val anonymousQueries: List<Pair<Namespace, TaxiParser.AnonymousQueryContext>>,
   val topLevelExpressions: List<TaxiParser.ExpressionGroupContext>,
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
         val unparsedTypes: MutableList<Triple<String, Namespace, ParserRuleContext>> = mutableListOf()
         val unparsedInlineTypes: MutableList<Pair<String, String>> = mutableListOf()
         val unparsedExtensions: MutableList<Pair<Namespace, ParserRuleContext>> = mutableListOf()
         val unparsedServices: MutableList<Triple<String, Namespace, ServiceDeclarationContext>> = mutableListOf()
         val unparsedPolicies: MutableList<Triple<String, Namespace, TaxiParser.PolicyDeclarationContext>> = mutableListOf()
         val unparsedFunctions: MutableList<Triple<String, Namespace, TaxiParser.FunctionDeclarationContext>> = mutableListOf()
         val namedQueries: MutableList<Triple<String, Namespace, TaxiParser.NamedQueryContext>> = mutableListOf()
         val anonymousQueries: MutableList<Pair<Namespace, TaxiParser.AnonymousQueryContext>> = mutableListOf()
         val topLevelExpressions: MutableList<TaxiParser.ExpressionGroupContext> = mutableListOf()
         members.forEach { tokens ->
            imports.addAll(tokens.imports)
            unparsedTypes.addAll(tokens.unparsedTypes)
            unparsedInlineTypes.addAll(tokens.unparsedInlineTypes)
            unparsedExtensions.addAll(tokens.unparsedExtensions)
            unparsedServices.addAll(tokens.unparsedServices)
            unparsedPolicies.addAll(tokens.unparsedPolicies)
            unparsedFunctions.addAll(tokens.unparsedFunctions)
            namedQueries.addAll(tokens.namedQueries)
            anonymousQueries.addAll(tokens.anonymousQueries)
            topLevelExpressions.addAll(tokens.topLevelExpressions)
         }
         val tokenStores = members.map { it.tokenStore }
         val tokenStore = TokenStore.combine(tokenStores)

         return Tokens(
            imports,
            unparsedTypes,
            unparsedInlineTypes,
            unparsedExtensions,
            unparsedServices,
            unparsedPolicies,
            unparsedFunctions,
            namedQueries,
            anonymousQueries,
            topLevelExpressions,
            tokenStore
         )
      }
   }

   val unparsedTypeNames: Set<QualifiedName> by lazy {
      unparsedTypes.map { (name, _, _) -> QualifiedName.from(name) }.toSet()
   }

   private val typeNamesBySource: Map<String, List<QualifiedName>> by lazy {
      unparsedTypes.map { (name, _, context) ->
         context.source().normalizedSourceName to QualifiedName.from(name)
      }.groupBy { it.first }
         .mapValues { (_, value) -> value.map { it.second } }
   }
   private val importsBySourceName: Map<String, List<Pair<String, TaxiParser.ImportDeclarationContext>>> by lazy {
      imports.groupBy { it.second.source().normalizedSourceName }
   }

   /**
    * Detects duplicate symbol declarations across all named symbols (types, services, policies, functions, named queries).
    * It is illegal to declare multiple symbols with the same qualified name, even if they are of different kinds.
    * For example, you cannot have both a type and a service with the same name.
    *
    * This includes:
    * - Duplicates within each symbol collection
    * - Duplicates between unparsedTypes and unparsedInlineTypes
    * - Duplicates across different symbol kinds (type vs service vs function etc)
    *
    * @param severity The severity level to use for duplicate symbol errors (ERROR, WARNING, or INFO)
    */
   fun detectDuplicates(
      severity: Severity,
      importSources: List<TaxiDocument>,
      builtInCompiledTaxi: TaxiDocument,
      importSymbolFilter: ImportSymbolFilter
   ): List<CompilationError> {
      val duplicateDefinitionsFromImports = tokenStore.collectDuplicateDeclarationsDetectedInImports(importSources, builtInCompiledTaxi, importSymbolFilter)
         .map { (nameOfConflictingSymbol, pair) ->
            val (placesDeclaredInTheseSources,placesDeclaredInImportedSources) = pair
            placesDeclaredInTheseSources.map { redeclarationSite ->
               val originalCompilationUnit = placesDeclaredInImportedSources.firstOrNull() ?: CompilationUnit.unspecified()
               val originalLocation = originalCompilationUnit.locationDescription
               CompilationError(redeclarationSite.start, "Symbol $nameOfConflictingSymbol is already declared at $originalLocation", severity = severity)
            }
         }.flatten()
      val duplicateSymbols = tokenStore.collectDuplicateDeclarationsDetectedInSources()
      val duplicateDefinitionsInSources = duplicateSymbols.flatMap { (nameOfConflictingSymbol, declarations) ->
         val (first,duplicates) = declarations.toList().takeHead()
         val firstLocation = "${first.source().sourceName} line ${first.start.line}, char ${first.start.charPositionInLine}"
         duplicates.map { duplicateDefinition ->
            CompilationError(duplicateDefinition.start, "Symbol $nameOfConflictingSymbol is already declared at $firstLocation", severity = severity)
         }
      }
      return duplicateDefinitionsInSources + duplicateDefinitionsFromImports
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
      return this.unparsedTypes.any { (name, _, _) -> name == qualifiedName } ||
             this.unparsedFunctions.any { (name, _, _) -> name == qualifiedName }
   }

   fun containsUnparsedType(qualifiedTypeName: String, symbolKind: SymbolKind): Boolean {
      return when {
         containsUnparsedInlineType(qualifiedTypeName) -> true
         else -> {
            val unparsedToken = this.unparsedTypes.find { (name, _, _) -> name == qualifiedTypeName }
            unparsedToken != null && symbolKind.matches(unparsedToken.third)
         }
      }
   }

   private fun containsUnparsedInlineType(qualifiedName: String): Boolean {
      return this.unparsedInlineTypes.any { (name, _) -> name == qualifiedName }
   }

   fun containsUnparsedService(qualifiedName: String): Boolean {
      return this.unparsedServices.any { (name, _, _) -> name == qualifiedName }
   }

}


class TokenCollator : TaxiBaseListener() {
   val exceptions = mutableMapOf<ParserRuleContext, Exception>()
   private var namespace: String = Namespaces.DEFAULT_NAMESPACE
   private var imports = mutableListOf<Pair<String, TaxiParser.ImportDeclarationContext>>()

   private val unparsedTypes = mutableListOf<Triple<String, Namespace, ParserRuleContext>>()

   // Inline types are a list of pairs: (name of inline type, name of model type that declares it inline)
   private val unparsedInlineTypes = mutableListOf<Pair<String, String>>()
   private val unparsedExtensions = mutableListOf<Pair<Namespace, ParserRuleContext>>()
   private val unparsedServices = mutableListOf<Triple<String, Namespace, ServiceDeclarationContext>>()
   private val unparsedPolicies = mutableListOf<Triple<String, Namespace, TaxiParser.PolicyDeclarationContext>>()
   private val unparsedFunctions = mutableListOf<Triple<String, Namespace, TaxiParser.FunctionDeclarationContext>>()
   private val namedQueries = mutableListOf<Triple<String, Namespace, TaxiParser.NamedQueryContext>>()
   private val anonymousQueries = mutableListOf<Pair<Namespace, TaxiParser.AnonymousQueryContext>>()
   private val topLevelExpressions = mutableListOf<TaxiParser.ExpressionGroupContext>()


   //    private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
//    private val unparsedExtensions = mutableListOf<ParserRuleContext>()
//    private val unparsedServices = mutableMapOf<String, ServiceDeclarationContext>()
   private val tokenStore = TokenStore()
   fun tokens(): Tokens {
      return Tokens(
         imports,
         unparsedTypes,
         unparsedInlineTypes,
         unparsedExtensions,
         unparsedServices,
         unparsedPolicies,
         unparsedFunctions,
         namedQueries,
         anonymousQueries,
         topLevelExpressions,
         tokenStore
      )
   }


   override fun exitEveryRule(ctx: ParserRuleContext) {
      val zeroBasedLineNumber = ctx.start.line - 1

      // The source can be unknown if we created a fake token
      // during error recovery
      val sourceName = ctx.start.tokenSource?.sourceName?.let { SourceNames.normalize(it) } ?: "UnknownSource"
      tokenStore.insert(sourceName, zeroBasedLineNumber, ctx.start.charPositionInLine, ctx)
   }

   override fun exitImportDeclaration(ctx: TaxiParser.ImportDeclarationContext) {
      if (collateExceptions(ctx)) {
         imports.add(ctx.qualifiedName().identifier().text() to ctx)
      }
      super.exitImportDeclaration(ctx)
   }

   override fun exitExpressionGroup(ctx: TaxiParser.ExpressionGroupContext) {
      if (ctx.parent is ToplevelObjectContext) {
         topLevelExpressions.add(ctx)
      }
      super.exitExpressionGroup(ctx)
   }


   override fun exitFieldDeclaration(ctx: TaxiParser.FieldDeclarationContext) {
      collateExceptions(ctx)
      // Check to see if an inline type alias is declared
      // If so, mark it for processing later
      val fieldCtx = ctx.fieldTypeDeclaration()

      // If there's an inline type declared, mark it so that we can process it later.
      if (fieldCtx?.inlineInheritedType() != null) {
         val owningType = fieldCtx.searchUpForRule(
            listOf(TypeDeclarationContext::class.java, AnnotationTypeDeclarationContext::class.java))
            ?: error("Field ${ctx.identifier()} declares an inline type - expected to find a parent type declaration, but didn't")

         val inlineTypeName = qualify(fieldCtx.typeExpression().nullableTypeReference().typeReference().qualifiedName().text)
         val owningTypeName = when (owningType) {
            is TypeDeclarationContext -> qualify(owningType.identifier().text)
            is AnnotationTypeDeclarationContext -> qualify(owningType.identifier().text)
            else -> error("Unexpected context in resolving inline field : ${owningType::class.simpleName}")
         }



         unparsedInlineTypes.add(inlineTypeName to owningTypeName)
      }
      super.exitFieldDeclaration(ctx)
   }


   override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
      if (collateExceptions(ctx)) {
         val name = qualify(ctx.identifier().text)
         unparsedTypes.add(Triple(name, namespace, ctx))
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
         val qualifiedName = qualify(ctx.identifier().text)
         unparsedPolicies.add(Triple(qualifiedName, namespace, ctx))
      }
      super.exitPolicyDeclaration(ctx)
   }

   override fun exitServiceDeclaration(ctx: ServiceDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.identifier().text)
         unparsedServices.add(Triple(qualifiedName, namespace, ctx))
      }
      super.exitServiceDeclaration(ctx)
   }

   override fun exitFunctionDeclaration(ctx: TaxiParser.FunctionDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.identifier().text)
         unparsedFunctions.add(Triple(qualifiedName, namespace, ctx))
      }
   }

   override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes.add(Triple(typeName,namespace,ctx))
      }
      super.exitTypeDeclaration(ctx)
   }
   override fun exitPartialModelDeclaration(ctx: TaxiParser.PartialModelDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes.add(Triple(typeName,namespace,ctx))
      }
      super.exitPartialModelDeclaration(ctx)
   }

   override fun exitTypeAliasDeclaration(ctx: TaxiParser.TypeAliasDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.identifier().text)
         unparsedTypes.add(Triple(typeName,namespace,ctx))
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
         unparsedTypes.add(Triple(typeName,namespace,ctx))
      }
      super.exitAnnotationTypeDeclaration(ctx)
   }

   override fun exitNamedQuery(ctx: TaxiParser.NamedQueryContext) {
      val queryName = qualify(ctx.queryName().identifier().text)
      namedQueries.add(Triple(queryName, namespace, ctx))
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
