package lang.taxi.query

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.mutations.Mutation
import lang.taxi.query.commands.CommandExpression
import lang.taxi.query.commands.ReadQueryCommand
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
   val parameters: List<Parameter>,
   val commands : List<CommandExpression>,
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


   internal val finalCommandAsReadOperation: ReadQueryCommand
      get() {
         val lastCommand = commands.last()
         require(lastCommand is ReadQueryCommand) { "Final step of the query is not a read command" }
         return lastCommand
      }

   // convenience call for backwards compatibility
   val returnType: Type
      get() {
         return commands.last().returnType
      }


   // convenience call for backwards compatibility
   val projectedObjectType: ObjectType?
      get() {
         val finalCommand = commands.last()
         return if (finalCommand is ReadQueryCommand) {
            finalCommand.projectedObjectType
         } else {
            null
         }
      }

   val typesToFind:List<DiscoveryType>
      get() {
         return finalCommandAsReadOperation.typesToFind
      }

}
