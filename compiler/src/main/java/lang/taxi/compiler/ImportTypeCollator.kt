package lang.taxi.compiler

import lang.taxi.CompilationError
import lang.taxi.CompilationException
import lang.taxi.TaxiDocument
import lang.taxi.Tokens
import lang.taxi.types.PrimitiveType
import lang.taxi.types.SourceNames
import lang.taxi.types.Type
import lang.taxi.types.UserType
import org.antlr.v4.runtime.Token
import java.util.*

internal class ImportedTypeCollator(
   private val tokens: Tokens,
   private val importSources: List<TaxiDocument>
) {
   fun collect(): Pair<List<CompilationError>, List<Type>> {
      val collected = mutableMapOf<String, Type>()
      val importQueue = LinkedList<Pair<String, Token>>()
      val errors = mutableListOf<CompilationError>()

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
         if (type == null) {
            errors.add(CompilationError(token, "Cannot import $name as it is not defined", token.tokenSource.sourceName))
         } else {
            collected[name] = type

            if (type is UserType<*, *>) {
               type.referencedTypes.forEach { importQueue.add(it.qualifiedName to token) }
            }
         }
      }

      return if (importQueue.isEmpty()) {
         errors to importSources.flatMap { it.types }
      } else {
         errors to collected.values.toList()
      }
   }


   private fun getType(name: String, referencingToken: Token): Type? {
      val type = this.importSources.firstOrNull { it.containsType(name) }?.type(name)
      return type
   }
}
