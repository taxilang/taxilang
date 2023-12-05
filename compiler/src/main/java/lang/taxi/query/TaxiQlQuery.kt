package lang.taxi.query

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.mutations.Mutation
import lang.taxi.types.*
import lang.taxi.types.Annotation


data class TaxiQlQuery(
   val name: QualifiedName,
   val facts: List<Parameter>,
   val queryMode: QueryMode,
   val parameters: List<Parameter>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: Type?,
   val projectionScope: ProjectionFunctionScope?,
   val mutation: Mutation?,
   override val typeDoc: String?,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>
) : Documented, Annotatable, Compiled {

   val source:TaxiQLQueryString = compilationUnits.joinToString("\n") { it.source.content }
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
            else -> {
               typesToFind.singleOrNull()?.anonymousType
                  ?: typesToFind.singleOrNull()?.type
                  ?: error("Could not infer return type of query.")
            }
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
