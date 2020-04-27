package lang.taxi.compiler

import lang.taxi.CompilationException
import lang.taxi.TaxiDocument
import lang.taxi.Tokens
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.UserType
import org.antlr.v4.runtime.Token
import java.util.*

internal class ImportedTypeCollator(
   private val tokens: Tokens,
   private val importSources: List<TaxiDocument>
) {
   fun collect(): List<Type> {
      val collected = mutableMapOf<String, Type>()
      val importQueue = LinkedList<Pair<String, Token>>()

      tokens.imports
         // Ignore any imports that are defined in the set of tokens we're importing.
         // THis happens when we're importing multiple files at once
         .filter { (qualifiedName, _) -> !tokens.unparsedTypes.containsKey(qualifiedName) }
         .forEach { (qualifiedName, ctx) ->
            importQueue.add(qualifiedName to ctx.start)
         }

      while (importQueue.isNotEmpty()) {
         val (name, token) = importQueue.pop()
         if (collected.containsKey(name)) continue
         if (PrimitiveType.isPrimitiveType(name)) continue

         val type = getType(name, token)
         collected[name] = type

         if (type is UserType<*, *>) {
            type.referencedTypes.forEach { importQueue.add(it.qualifiedName to token) }
         }
      }
      return collected.values.toList()
   }


   private fun getType(name: String, referencingToken: Token): Type {
      val type = this.importSources.firstOrNull { it.containsType(name) }?.type(name)
      return type
         ?: throw CompilationException(referencingToken, "Cannot import $name as it is not defined", referencingToken.tokenSource.sourceName)
   }
}
