package lang.taxi.compiler

import lang.taxi.CompilationException
import lang.taxi.TaxiDocument
import lang.taxi.TaxiParser
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.UserType
import org.antlr.v4.runtime.Token
import java.util.*

internal class ImportedTypeCollator(val imports: List<Pair<String, TaxiParser.ImportDeclarationContext>>, val importSources: List<TaxiDocument>) {
   fun collect(): List<Type> {
      val collected = mutableMapOf<String, Type>()
      val importQueue = LinkedList<Pair<String, Token>>()

      imports.forEach { (qualifiedName, ctx) ->
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
      return type ?: throw CompilationException(referencingToken, "Cannot import $name as it is not defined")
   }
}
