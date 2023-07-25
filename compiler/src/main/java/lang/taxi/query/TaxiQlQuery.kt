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
   val returnType: ObjectType
      get() {
         return projectedObjectType ?: typesToFind.singleOrNull()?.anonymousType as ObjectType?
         ?: typesToFind.singleOrNull()?.type as ObjectType?
         ?: error("Could not infer return type of query.")
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
