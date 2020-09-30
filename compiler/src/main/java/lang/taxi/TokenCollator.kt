package lang.taxi

import com.google.common.collect.ArrayListMultimap
import lang.taxi.TaxiParser.ServiceDeclarationContext
import lang.taxi.compiler.TokenProcessor
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
   val tokenStore: TokenStore
) {

   val unparsedTypeNames: Set<QualifiedName> by lazy {
      unparsedTypes.keys.map { QualifiedName.from(it) }.toSet()
   }

   private val typeNamesBySource: Map<String, List<QualifiedName>> by lazy {
      unparsedTypes.map { (name, namespaceContextPair) ->
         val (_, context) = namespaceContextPair
         context.source().normalizedSourceName to QualifiedName.from(name)
      }.groupBy { it.first }
         .mapValues { (key, value) -> value.map { it.second } }
   }
   private val importsBySourceName: Map<String, List<Pair<String, TaxiParser.ImportDeclarationContext>>> by lazy {
      imports.groupBy { it.second.source().normalizedSourceName }
   }

   fun plus(others: Tokens): Tokens {
      // Duplicate checking is disabled, as it doesn't consider imports, which causes false compilation errors
        val errorsFromDuplicates = collectDuplicateTypes(others) + collectDuplicateServices(others)
        if (errorsFromDuplicates.isNotEmpty()) {
          throw CompilationException(errorsFromDuplicates)
        }
      return Tokens(
         this.imports + others.imports,
         this.unparsedTypes + others.unparsedTypes,
         this.unparsedExtensions + others.unparsedExtensions,
         this.unparsedServices + others.unparsedServices,
         this.unparsedPolicies + others.unparsedPolicies,
         this.unparsedFunctions + others.unparsedFunctions,
         this.tokenStore + others.tokenStore
      )
   }

   private fun collectDuplicateTypes(others: Tokens): List<CompilationError> {
      val duplicateTypeNames = this.unparsedTypes.keys.filter { others.unparsedTypes.containsKey(it) }
      val errors = if (duplicateTypeNames.isNotEmpty()) {
         val compilationErrors = duplicateTypeNames.map {
            CompilationError((others.unparsedTypes[it] ?: error("")).second.start, "Attempt to redefine type $it. Types may be extended (using an extension), but not redefined")
         }
         compilationErrors
      } else emptyList()
      return errors
   }

   private fun collectDuplicateServices(others: Tokens): List<CompilationError> {
      val duplicateServices = this.unparsedServices.keys.filter { others.unparsedServices.containsKey(it) }
      val errors = duplicateServices.map { CompilationError(others.unparsedServices[it]!!.second.start, "Attempt to redefine service $it. Services may be extended (using an extension), but not redefined") }
      return errors
   }

   fun importTokensInSource(sourceName: String):List<Pair<QualifiedName,TaxiParser.ImportDeclarationContext>> {
      return importsBySourceName.getOrDefault(SourceNames.normalize(sourceName), emptyList())
         .map { (name,token) -> QualifiedName.from(name) to token }
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

}


class TokenCollator : TaxiBaseListener() {
   val exceptions = mutableMapOf<ParserRuleContext, Exception>()
   private var namespace: String = Namespaces.DEFAULT_NAMESPACE
   private var imports = mutableListOf<Pair<String, TaxiParser.ImportDeclarationContext>>()

   private val unparsedTypes = mutableMapOf<String, Pair<Namespace, ParserRuleContext>>()
   private val unparsedExtensions = mutableListOf<Pair<Namespace, ParserRuleContext>>()
   private val unparsedServices = mutableMapOf<String, Pair<Namespace, ServiceDeclarationContext>>()
   private val unparsedPolicies = mutableMapOf<String, Pair<Namespace, TaxiParser.PolicyDeclarationContext>>()
   private val unparsedFunctions  = mutableMapOf<String, Pair<Namespace, TaxiParser.FunctionDeclarationContext>>()

   //    private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
//    private val unparsedExtensions = mutableListOf<ParserRuleContext>()
//    private val unparsedServices = mutableMapOf<String, ServiceDeclarationContext>()
   private val tokenStore = TokenStore()
   fun tokens(): Tokens {
      return Tokens(imports, unparsedTypes, unparsedExtensions, unparsedServices, unparsedPolicies, unparsedFunctions, tokenStore)
   }

   override fun exitEveryRule(ctx: ParserRuleContext) {
      val zeroBasedLineNumber = ctx.start.line - 1

      val sourceName = SourceNames.normalize(ctx.start.tokenSource.sourceName)
      tokenStore.insert(sourceName, zeroBasedLineNumber, ctx.start.charPositionInLine, ctx)
   }

   override fun exitImportDeclaration(ctx: TaxiParser.ImportDeclarationContext) {
      if (collateExceptions(ctx)) {
         imports.add(ctx.qualifiedName().Identifier().text() to ctx)
      }
      super.exitImportDeclaration(ctx)
   }


   override fun exitFieldDeclaration(ctx: TaxiParser.FieldDeclarationContext) {
      collateExceptions(ctx)
      // Check to see if an inline type alias is declared
      // If so, mark it for processing later
      val typeType = ctx.typeType()
      if (typeType?.aliasedType() != null) {
         val classOrInterfaceType = typeType.classOrInterfaceType()
         unparsedTypes.put(qualify(classOrInterfaceType.Identifier().text()), namespace to typeType)
      }
      super.exitFieldDeclaration(ctx)
   }

   override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
      if (collateExceptions(ctx)) {
         val name = qualify(ctx.classOrInterfaceType().Identifier().text())
         unparsedTypes.put(name, namespace to ctx)
      }
      super.exitEnumDeclaration(ctx)
   }

   override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
      collateExceptions(ctx)
      ctx.qualifiedName()?.Identifier()?.text()?.let { namespace -> this.namespace = namespace }
      super.exitNamespaceDeclaration(ctx)
   }

   override fun enterNamespaceBody(ctx: TaxiParser.NamespaceBodyContext) {
      val parent = ctx.parent as ParserRuleContext
      val namespaceNode = parent.getChild(TaxiParser.QualifiedNameContext::class.java, 0)
      this.namespace = namespaceNode.Identifier().text()
      super.enterNamespaceBody(ctx)
   }

   override fun exitPolicyDeclaration(ctx: TaxiParser.PolicyDeclarationContext) {
      if (collateExceptions(ctx)) {
         // TODO : Why did I have to change this?  Why is Identifier() retuning null now?
         // Was:  qualify(ctx.policyIdentifier().Identifier().text)
         val qualifiedName = qualify(ctx.policyIdentifier().text)
         unparsedPolicies[qualifiedName] = namespace to ctx
      }
      super.exitPolicyDeclaration(ctx)
   }

   override fun exitServiceDeclaration(ctx: ServiceDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.Identifier().text)
         unparsedServices[qualifiedName] = namespace to ctx
      }
      super.exitServiceDeclaration(ctx)
   }

   override fun exitFunctionDeclaration(ctx: TaxiParser.FunctionDeclarationContext) {
      if (collateExceptions(ctx)) {
         val qualifiedName = qualify(ctx.functionName().qualifiedName().Identifier().text())
         unparsedFunctions[qualifiedName] = namespace to ctx
      }
   }

   override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.Identifier().text)
         unparsedTypes[typeName] = namespace to ctx
      }
      super.exitTypeDeclaration(ctx)
   }

   override fun exitTypeAliasDeclaration(ctx: TaxiParser.TypeAliasDeclarationContext) {
      if (collateExceptions(ctx)) {
         val typeName = qualify(ctx.Identifier().text)
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
