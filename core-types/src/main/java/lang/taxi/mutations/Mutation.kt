package lang.taxi.mutations

import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled

data class Mutation(
   val service: Service,
   val operation: Operation,
   override val compilationUnits: List<CompilationUnit>
) : Compiled
