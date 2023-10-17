package lang.taxi.compiler

import lang.taxi.CompilationError
import lang.taxi.TaxiDocument
import lang.taxi.Tokens
import lang.taxi.types.ArrayType
import lang.taxi.types.ImportableToken
import lang.taxi.types.MapType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.UserType
import org.antlr.v4.runtime.Token
import java.util.*


internal class ImportedTypeCollator(
   private val tokens: Tokens,
   private val importSources: List<TaxiDocument>
) {
   fun collect(): Pair<List<CompilationError>, List<ImportableToken>> {
      val collected = mutableMapOf<String, ImportableToken>()
      val importQueue = LinkedList<Pair<String, Token>>()
      val errors = mutableListOf<CompilationError>()

      tokens.imports
         // Ignore any imports that are defined in the set of tokens we're importing.
         // THis happens when we're importing multiple files at once
         .filter { (qualifiedName, _) -> !tokens.hasUnparsedImportableToken(qualifiedName) }
         .forEach { (qualifiedName, ctx) ->
            importQueue.add(qualifiedName to ctx.start)
         }

      while (importQueue.isNotEmpty()) {
         val (name, token) = importQueue.pop()
         if (collected.containsKey(name)) continue
         if (PrimitiveType.isPrimitiveType(name)) continue

         // We don't need to import if it's defined elsewhere in these sources.
         if (tokens.containsUnparsedType(name, SymbolKind.TYPE)) continue

         val type = getImportableToken(name, token)
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
         // TODO : This is pretty brute-force.
         // No imports were specified, so we return all the types.
         // Need to reconsider what we actually want here.
         errors to importSources.flatMap { it.types.plus(it.functions).plus(it.services) }
      } else {
         errors to collected.values.toList()
      }
   }


   private fun getImportableToken(name: String, referencingToken: Token): ImportableToken? {
      // Allow imports of primitive and built-in types to not cause errors
      if (PrimitiveType.isPrimitiveType(name)) {
         return PrimitiveType.fromDeclaration(name)
      }
      if (ArrayType.isArrayTypeName(name)) {
         return ArrayType.untyped()
      }
      if (StreamType.isStreamTypeName(name)) {
         return StreamType.untyped()
      }
      if (MapType.isMapTypeName(name)) {
         return MapType.untyped()
      }

      val importableToken = this.importSources.firstOrNull { it.containsImportable(name) }?.importableToken(name)
      return importableToken
   }
}
