package lang.taxi.codegen.ts.react

import lang.taxi.TaxiDocument
import lang.taxi.codegen.BaseCodeGenerator
import lang.taxi.codegen.GeneratedCode
import lang.taxi.codegen.ts.TypedQueryGenerator
import lang.taxi.codegen.ts.TypescriptModelGenerator
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.ArrayType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import org.antlr.v4.runtime.CodePointCharStream

class ReactCodegen : BaseCodeGenerator() {
   override fun generateCodeForQueries(
      queries: Set<TaxiQlQuery>,
      src: TaxiDocument,
      inlineSourceFiles: List<CodePointCharStream>
   ): List<GeneratedCode> {
      val returnTypes = queries.map { query -> query.returnType }

      // By default, query response types have a name of AnonymousTypeXxxxx
      // Here we rename them to something more human-friendly
      val renamedReturnTypes = queries.map { query ->
         if (query.returnType.anonymous) {
            val typeNameToModify = unwrap(query.returnType).toQualifiedName()
            typeNameToModify to QualifiedName.from("${query.name.typeName}Result")
         } else {
            query.returnType.toQualifiedName() to query.returnType.toQualifiedName()
         }
      }.toMap()


      val (typescriptNames, modelCode) = TypescriptModelGenerator(returnTypes, nameSubstitutions = renamedReturnTypes)
         .build()

      val queriesAndResponseTypes = queries.map { taxiQlQuery ->
         val returnTypeName = unwrap(taxiQlQuery.returnType).toQualifiedName()
         val generatedResponseType = typescriptNames[returnTypeName] ?: error("Expected a typescript Type to be generated for return type from query ${taxiQlQuery.name} (${returnTypeName.parameterizedName}) but one was not found")
         taxiQlQuery to generatedResponseType
      }.toMap()
      val queriesCode = TypedQueryGenerator().generate(queriesAndResponseTypes, originalSources = inlineSourceFiles)

      return listOf(modelCode,queriesCode)
   }


   private fun unwrap(type:Type):Type {
      return when (type) {
         is ArrayType -> type.memberType
         is StreamType -> type.type
         else -> type
      }
   }
}


