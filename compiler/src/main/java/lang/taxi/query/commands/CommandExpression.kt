package lang.taxi.query.commands

import lang.taxi.types.Annotatable
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.Type

/**
 * A command expression is a top-level expression within a query
 * Things like find, stream, mutate, etc.
 */
sealed interface CommandExpression : Compiled {
   val returnType: Type
}
