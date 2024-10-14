package lang.taxi

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.compiler.TokenProcessor
import lang.taxi.compiler.CircularReferenceException
import lang.taxi.types.DefinableToken
import lang.taxi.types.HasChildSymbols
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.NamespaceToken
import lang.taxi.types.PrimitiveType
import lang.taxi.types.toQualifiedName
import lang.taxi.utils.createCompilationError
import lang.taxi.utils.flatMapLeft
import lang.taxi.utils.takeHead
import org.antlr.v4.runtime.ParserRuleContext
import java.util.concurrent.ConcurrentHashMap

private object TreeRootNode : Named {
   override val qualifiedName: String = ""
}

class SymbolTree {
   companion object {
      fun withPrimitives(): SymbolTree {
         val tree = SymbolTree()
         PrimitiveType.values().forEach {
            tree.register(it)
         }
         return tree
      }


   }

   private val root = SymbolTreeNode("", TreeRootNode)
   private val symbolsByName = ConcurrentHashMap<String, Named>()

   fun registerAll(symbols: Iterable<ImportableToken>): SymbolTree {
      symbols.forEach { register(it) }
      return this
   }

   fun register(symbol: ImportableToken): SymbolTree {
      val nameParts = symbol.qualifiedName.split(".")
      val (head, remaining) = nameParts.takeHead()
      val existing = symbolsByName.putIfAbsent(symbol.qualifiedName, symbol)
      if (existing != null) {
         // Is this a problem?
//         error("Attempt to redefine symbol ${symbol.qualifiedName}")
      }
      doRegister(head, remaining, symbol, root)
      return this
   }

   private fun doRegister(
      namePart: String,
      remaining: List<String>,
      symbol: ImportableToken,
      parent: SymbolTreeNode
   ): SymbolTreeNode {
      val thisTreeNode = parent.children.getOrPut(namePart) {
         val tokenValue = if (remaining.isNotEmpty()) {
            val qualifiedName = joinNameParts(parent.node, namePart)
            NamespaceToken(qualifiedName)
         } else {
            symbol
         }
         SymbolTreeNode(namePart, tokenValue)
      }
      return if (remaining.isEmpty()) {
         thisTreeNode
      } else {
         val (next, nextRemaining) = remaining.takeHead()
         doRegister(next, nextRemaining, symbol, thisTreeNode)
      }
   }

   private fun findMostSpecificTreeNode(
      name: String,
      node: SymbolTreeNode
   ): Triple<SymbolTreeNode, TextFragmentWithCompiledToken, List<String>> {
      val nameParts = name.split(".")
      val requestedNode = nameParts.foldIndexed(node) { index, treeNode, namePart ->
         val nextNode = treeNode.children[namePart]
         @Suppress("IfThenToElvis")
         if (nextNode == null) {
            // Couldn't match any further, so the value of treeNode
            // the most specific node we could find.
            // (could be the root)
            // return the node, along with the remaining parts of the requested name
            val matchedNameParts = nameParts.subList(0, index).joinToString(".")
            return Triple(
               treeNode,
               TextFragmentWithCompiledToken(matchedNameParts, treeNode.node),
               nameParts.subList(index, nameParts.size)
            )
         } else {
            nextNode
         }
      }
      val matchedToken = TextFragmentWithCompiledToken(name, requestedNode.node)
      return Triple(requestedNode, matchedToken, emptyList())
   }


   private fun resolveViaImports(
      nameToMatch: String,
      currentNamespace: String,
      imports: List<String>
   ): Either<String,TextFragmentWithCompiledToken> {
      // MP 12-Oct
      // don't try to resolve multi-part tokens here.
      // If it's a fully-qualified name (foo.bar.Person), it's not going to resolve as an import.
      // If it's reference with properties and extension-functions, (eg: Countries.NZ.flagColour())
      // you need to handle & iterate the remaining tokens.
      if (nameToMatch.contains(".")) {
         return "An internal error occurred: Symbol '$nameToMatch' should only attempt to resolve a single symbol via imports".left()
      }

      // Is the name imported?
      val matchedByImport = imports.filter { it.endsWith(nameToMatch) }
         .singleOrNull()?.let { matchingImport ->
            symbolsByName[matchingImport] ?:
            return ErrorMessages.unresolvedType(nameToMatch).left()
         }
      if (matchedByImport != null) {
         return TextFragmentWithCompiledToken(nameToMatch, matchedByImport).right()
      }

      // Is the name unambiguous?

      val matchesByName = symbolsEndingWithName(nameToMatch)
      if (matchesByName.size == 1) {
         return TextFragmentWithCompiledToken(nameToMatch,matchesByName.single()).right()
      }

      // Does the symbol exist in the same namespace?
      // If so, no imports are neccessary
      val matchesInNamespace = symbolsEndingWithName(
         joinNameParts(currentNamespace, nameToMatch)
      )
      if (matchesInNamespace.size == 1) {
         return TextFragmentWithCompiledToken(nameToMatch,matchesInNamespace.single()).right()
      }

      // Not sure what else to try - give up
      return ErrorMessages.unresolvedType(nameToMatch).left()
   }

   private fun symbolsEndingWithName(searchName: String): List<Named> {
      return this.symbolsByName.values.filter { it.qualifiedName == searchName || it.qualifiedName.toQualifiedName().typeName == searchName }
   }

   private fun joinNameParts(parent: Named, namePart: String): String {
      val parentName = parent.toQualifiedName().fullyQualifiedName
      return if (parentName.isEmpty()) { // empty when it's the root
         namePart
      } else {
         joinNameParts(parentName, namePart)
      }
   }

   private fun joinNameParts(a: String, b: String): String {
      return if (a.isEmpty()) {
         b
      } else {
         listOf(a, b).joinToString(".")
      }
   }

   /**
    * Requests the token processor to compile the referenced token, if it hasn't yet
    * been compiled.
    */
   private fun compileIfRequired(
      token: TextFragmentWithCompiledToken,
      tokenProcessor: TokenProcessor,
      context: ParserRuleContext,
      // If false, this method does nothing.
      // Only here to simplify call chains.
      shouldCompile: Boolean = true
   ): Either<List<CompilationError>, TextFragmentWithCompiledToken> {
      if (!shouldCompile) {
         return token.right()
      }
      return if (token.value is DefinableToken<*> && !token.value.isDefined) {
         tokenProcessor.compile(token.value, context).map { compiledToken ->
            if (!compiledToken.isDefined) {
               return listOf(CompilationError(
                  context.toCompilationUnit(),
                  "A circular reference was encountered when compiling ${token.value.qualifiedName}",
                  throwable = CircularReferenceException(compiledToken)
                  )).left()
            } else {
               token.copy(value = compiledToken)
            }
         }
      } else {
         token.right()
      }
   }

   fun getBestMatch(requestedName: String, currentNamespace: String, imports: List<String>): Pair<TextFragmentWithCompiledToken, List<String>> {
      val (mostSpecificNode, matchedToken, unresolvedNameParts) =  findMostSpecificTreeNode(requestedName, root)
      if (mostSpecificNode == root) {
         // MP 12:Oct
         // If the name to match is many parts, just try to match the first part,
         // Here's the rationale
         // If it's a namespace declaration, the reference is already absolute, so we
         // don't need to try to resolve via imports.
         // However, it could be the first token in a many-part statement (eg., Enum.Property.ExtensionFunction())
         // We only want to resolve the first part of that for now.
         val nameParts = requestedName.split(".")
         resolveViaImports(nameParts.first(), currentNamespace, imports)
            .map { resolved ->
               return resolved to nameParts.drop(1)
            }
      }
      return matchedToken to unresolvedNameParts
   }

   fun getSymbol(
      requestedName: String,
      currentNamespace: String = "",
      imports: List<String> = emptyList(),
      context: ParserRuleContext,
      // We need the token processor, as if we hit any symbols
      // that aren't yet compiled, we'll need to process them before
      // we can navigate into their child symbols.
      tokenProcessor: TokenProcessor,
      compileIfRequired: Boolean = true
   ): Either<List<CompilationError>, List<TextFragmentWithCompiledToken>> {
      val (mostSpecificNode, matchedToken, unresolvedNameParts) = findMostSpecificTreeNode(requestedName, root)
      if (unresolvedNameParts.isEmpty()) {
         return listOf(TextFragmentWithCompiledToken(requestedName, mostSpecificNode.node)).right()
      }

      if (mostSpecificNode == root) {
         val resolvedViaImports = resolveViaImports(
            unresolvedNameParts.first(),
            currentNamespace,
            imports
         ).getOrElse { return context.createCompilationError(it) }
         val remainingNameParts = unresolvedNameParts.drop(1)
         return if (remainingNameParts.isEmpty()) {
            listOf(resolvedViaImports).right()
         } else {
            resolveMember(resolvedViaImports, remainingNameParts, currentNamespace, imports, tokenProcessor, context, compileIfRequired)
         }
      } else {
         return resolveMember(matchedToken, unresolvedNameParts, currentNamespace, imports, tokenProcessor, context, compileIfRequired)
      }
   }

   /**
    * Resolves a list of strings to a member, considering imported values
    * for things like extension functions.
    *
    * There is a conflict in how implicit lookups are performed,
    * as both extension functions and enum defaults attempt to provide
    * behaviour for non-defined values.
    *
    * Therefore, for enums the following order-of-precedence exists:
    *  - Explicit values defined on an enum
    *  - Extension functions, matched by imported name (or declared in the same namespace)
    *  - Default values on the enum
    *  - Throw an error
    *
    */
   private fun resolveMember(
      named: TextFragmentWithCompiledToken,
      remainingNameParts: List<String>,
      currentNamespace: String,
      imports: List<String>,
      tokenProcessor: TokenProcessor,
      context: ParserRuleContext,
      compileIfRequired: Boolean
   ): Either<List<CompilationError>, List<TextFragmentWithCompiledToken>> {
      // If it has members (eg., operations, fields, enum values)
      // resolve that
      return remainingNameParts.fold(listOf(named)) { acc, name ->
         val last = compileIfRequired(acc.last(), tokenProcessor, context, compileIfRequired)
            .getOrElse { return it.left() }
         when (last.value) {
            is HasChildSymbols<*> -> {
               val resolvedMember = resolveTokenAsMember(last.value, name, currentNamespace, imports)
                  .getOrElse { return context.createCompilationError(it) }
               acc + resolvedMember
            }

            is NamespaceToken -> {
               val combinedToken = listOf(last.text, name).joinToString(".")
               if (symbolsByName.containsKey(combinedToken)) {
                  val resolvedMember = TextFragmentWithCompiledToken(combinedToken, symbolsByName[combinedToken]!!)
                  acc + resolvedMember
               } else {
                  return context.createCompilationError(ErrorMessages.unresolvedType(combinedToken))
               }
            }

            else -> {
               // It doesn't have any members.
               // Attempt to look up the member via an import.
               // This is really only allowable if the member
               // is an extension function
               resolveViaImports(
                  name,
                  currentNamespace,
                  imports
               ).map { importLookup ->
                  acc + importLookup
               }.getOrElse { return context.createCompilationError(it) }
            }
         }
      }.right()
   }

   private fun resolveTokenAsMember(
      tokenWithMembers: HasChildSymbols<*>,
      name: String,
      currentNamespace: String,
      imports: List<String>
   ): Either<String, TextFragmentWithCompiledToken> {
      val resolvedMember = tokenWithMembers.getMember(name, permitImplicitResolution = false)
         .map { resolvedMember -> TextFragmentWithCompiledToken(name, resolvedMember) }
         .flatMapLeft { error ->
            // It wasn't available as an explicit value on the member (but might be an implicit value - like an enum default)
            // First, check if this is resolved via imports (ie., is it an extension function?)
            resolveViaImports(
               name,
               currentNamespace,
               imports
            )
         }.flatMapLeft {
            // The parent didn't have a member matching the
            // requested name, and we coulnd't resolve it as an extension function
            // Try now using implicit resolution (ie., default values)
            // If this fails, we give up.
            tokenWithMembers.getMember(name, permitImplicitResolution = true)
               .map { resolvedMember -> TextFragmentWithCompiledToken(name, resolvedMember) }
         }.getOrElse { errorMessage ->
            return errorMessage.left()
         }
      return resolvedMember.right()
   }
}



/**
 * Models a single text part, mapped to a compiled token (Named),
 * and it's children.
 *
 * Eg:
 * com.foo.bar.Person would be 3 namespace nodes and a Type node.
 *
 * This class models the individual name parts as seen by the compiler.
 *
 *
 */
private data class SymbolTreeNode(
   val text: String,
   val node: Named,
   val children: ConcurrentHashMap<String, SymbolTreeNode> = ConcurrentHashMap()
)

/**
 * Contains the text of a compiled statement,
 * mapped to the fully qualified token that the text represents
 *
 * This class is returned when looking up values against a syntax tree.
 *
 * For example:
 *  - com.foo.bar.Person would be a single node.
 *  - Person (when in the namespace of com.foo.bar)
 *  - Person, when an import exists
 *
 *  etc
 */
data class TextFragmentWithCompiledToken(
   val text: String,
   // Named, not ImportableToken as a namespace isn't
   // importable
   val value: Named
)
