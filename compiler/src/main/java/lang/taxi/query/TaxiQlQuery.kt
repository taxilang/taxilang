package lang.taxi.query

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.mutations.Mutation
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.Arrays
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type


data class TaxiQlQuery(
   val name: QualifiedName,
   val facts: List<Parameter>,
   val queryMode: QueryMode,
   val parameters: List<Parameter>,
   val discoveryType: DiscoveryType?,
   val projectedType: Type?,
   val projectionScopeVars: List<ProjectionFunctionScope>,
   val mutation: Mutation?,
   override val typeDoc: String?,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>
) : Documented, Annotatable, Compiled {

   @Deprecated(
      "Only single discovery types are supported. Use discoveryType instead.",
      replaceWith = ReplaceWith("discoveryType")
   )
   val typesToFind: List<DiscoveryType> = listOfNotNull(discoveryType)

   val source: TaxiQLQueryString = compilationUnits.joinToString("\n") { it.source.content }

   /**
    * If the return type is a collection, returns
    * the member type.  Otherwise the actual return type is returned.
    */
   val unwrappedReturnType: Type
      get() {
         return Arrays.unwrapPossibleArrayType(returnType)
      }
   val returnType: Type
      get() {
         return when {
            mutation != null -> mutation.operation.returnType
            projectedObjectType != null -> projectedObjectType!!
            discoveryType != null -> discoveryType.expression.returnType
            else ->  error("Could not infer return type of query.")
         }
      }


   val projectedObjectType: ObjectType?
      get() {
         return when (projectedType) {
            null -> null
            is ArrayType -> projectedType.type as ObjectType
            is ObjectType -> projectedType
            else -> {
               error("Cannot cast ${projectedType::class.simpleName} to ObjectType")
            }
         }
      }


}
