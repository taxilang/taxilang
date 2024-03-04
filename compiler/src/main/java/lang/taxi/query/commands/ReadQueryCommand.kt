package lang.taxi.query.commands

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.query.DiscoveryType
import lang.taxi.query.Parameter
import lang.taxi.query.QueryMode
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.ObjectType
import lang.taxi.types.Type

data class ReadQueryCommand(
   val queryMode: QueryMode,
   val parameters: List<Parameter>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: Type?,
   val projectionScopeVars: List<ProjectionFunctionScope>,
   override val compilationUnits: List<CompilationUnit>
) : CommandExpression {
   override val returnType: Type
      get()  {
         return when {
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

