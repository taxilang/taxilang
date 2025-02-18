package lang.taxi.mutations

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Type

data class Mutation(
   val service: Service,
   val operation: Operation,
   val projectedType: Pair<Type, List<ProjectionFunctionScope>>?,
   override val compilationUnits: List<CompilationUnit>
) : Compiled
