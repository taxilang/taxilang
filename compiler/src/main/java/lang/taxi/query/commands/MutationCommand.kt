package lang.taxi.query.commands

import lang.taxi.mutations.Mutation
import lang.taxi.query.Parameter
import lang.taxi.types.Annotation
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Type

data class MutationCommand (
   val parameters: List<Parameter>,
   val mutation: Mutation,
   override val compilationUnits: List<CompilationUnit>
) : CommandExpression {
   override val returnType: Type = mutation.operation.returnType
}
