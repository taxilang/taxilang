package lang.taxi

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.types.HasMembers
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.NamespaceToken
import lang.taxi.types.PrimitiveType
import lang.taxi.types.toQualifiedName
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
            return Triple(treeNode, TextFragmentWithCompiledToken(matchedNameParts, treeNode.node), nameParts.subList(index, nameParts.size))
         } else {
            nextNode
         }
      }
      val matchedToken = TextFragmentWithCompiledToken(name, requestedNode.node)
      return Triple(requestedNode, matchedToken,emptyList())
   }

   private fun resolveViaImports(
      nameToMatch: String,
      currentNamespace: String,
      imports: List<String>
   ): Either<String,TextFragmentWithCompiledToken> {
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

   fun getSymbol(
      requestedName: String,
      currentNamespace: String = "",
      imports: List<String> = emptyList(),
      context: ParserRuleContext
   ): Either<List<CompilationError>, List<TextFragmentWithCompiledToken>> {
      return getSymbol(requestedName, currentNamespace, imports)
         .mapLeft { listOf(CompilationError(context.toCompilationUnit(), it)) }
   }

   fun getSymbol(
      requestedName: String,
      currentNamespace: String = "",
      imports: List<String> = emptyList(),

      ): Either<String, List<TextFragmentWithCompiledToken>> {
      val (mostSpecificNode,matchedToken, unresolvedNameParts) = findMostSpecificTreeNode(requestedName, root)
      if (unresolvedNameParts.isEmpty()) {
         return listOf(TextFragmentWithCompiledToken(requestedName,mostSpecificNode.node)).right()
      }

      if (mostSpecificNode == root) {
         val resolvedViaImports = resolveViaImports(
            unresolvedNameParts.first(),
            currentNamespace,
            imports
         ).getOrElse { return it.left() }
         val remainingNameParts = unresolvedNameParts.drop(1)
         return if (remainingNameParts.isEmpty()) {
            listOf(resolvedViaImports).right()
         } else {
            resolveMember(resolvedViaImports, remainingNameParts, currentNamespace, imports)
         }
      } else {
         return resolveMember(matchedToken, unresolvedNameParts, currentNamespace, imports)
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
      imports: List<String>
   ): Either<String, List<TextFragmentWithCompiledToken>> {
      // If it has members (eg., operations, fields, enum values)
      // resolve that
      return remainingNameParts.fold(listOf(named)) { acc, name ->
         val last = acc.last()
         if (last.value is HasMembers<*>) {
            val resolvedMember = last.value.getMember(name, permitImplicitResolution = false)
               .map { resolvedMember -> TextFragmentWithCompiledToken(name,resolvedMember) }
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
                  last.value.getMember(name, permitImplicitResolution = true)
                     .map { resolvedMember -> TextFragmentWithCompiledToken(name,resolvedMember) }
               }.getOrElse { errorMessage ->
                  return errorMessage.left()
               }

            acc + resolvedMember
         } else {
            // It doesn't have any members.
            // Attempt to look up the member via an import.
            // This is really only allowable if the member
            // is an extension function
            resolveViaImports(
               name,
               currentNamespace,
               imports
            ).map {  importLookup ->
               acc + importLookup
            }.getOrElse { return it.left() }
         }
      }.right()
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
