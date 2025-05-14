package lang.taxi.query

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.expressions.Expression
import lang.taxi.expressions.ProjectingExpression
import lang.taxi.mutations.Mutation
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.Arrays
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.ImportableToken
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import kotlin.reflect.jvm.internal.impl.metadata.ProtoBuf.Type.Argument.Projection


data class TaxiQlQuery(
   val name: QualifiedName,
   val facts: List<Parameter>,
   val queryMode: QueryMode,
   val parameters: List<Parameter>,
   val discoveryType: DiscoveryType?,
//   val projectedType: Type?,
//   val projectionScopeVars: List<ProjectionFunctionScope>,
   val projection: Expression?,
   val mutation: Mutation?,
   val serviceRestrictions: ServiceRestrictions = ServiceRestrictions.EMPTY,
   override val typeDoc: String?,
   override val annotations: List<Annotation>,
   override val compilationUnits: List<CompilationUnit>
) : Documented, Annotatable, Compiled, ImportableToken {

   override val qualifiedName: String = name.parameterizedName

   @Deprecated(
      "Only single discovery types are supported. Use discoveryType instead.",
      replaceWith = ReplaceWith("discoveryType")
   )
   val typesToFind: List<DiscoveryType> = listOfNotNull(discoveryType)

   val source: TaxiQLQueryString = compilationUnits.joinToString("\n") { it.source.content }

   // For backwards compatability
   val projectedType: Type? = projectingExpression?.returnType
   // For backwards compatability
   val projectionScopeVars: List<ProjectionFunctionScope> = projectingExpression?.projection?.projectionFunctionScope
      ?: emptyList()

   // In most cases, a projection should be a ProjectingExpression, rather than
   // some other expression type.
   // Can't think of a use-case that differs here
   val projectingExpression: ProjectingExpression?
      get() {
         return if (projection is ProjectingExpression) {
            projection
         } else null
      }

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
         val returnType =  when {
            mutation != null -> mutation.operation.returnType
            projectedType != null -> projectedType
            discoveryType != null -> discoveryType.expression.returnType
            else -> error("Could not infer return type of query.")
         }
         // If we're mapping, then we end up with a T[]
         return if (queryMode == QueryMode.MAP) {
            Arrays.arrayOf(returnType)
         } else returnType
      }


   val projectedObjectType: ObjectType?
      get() {
         return when (projectedType) {
            null -> null
            is StreamType -> projectedType.type as ObjectType
            is ArrayType -> projectedType.type as ObjectType
            is ObjectType -> projectedType
            else -> {
               error("Cannot cast ${projectedType::class.simpleName} to ObjectType")
            }
         }
      }


}
