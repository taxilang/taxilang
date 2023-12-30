package lang.taxi.services.operations.constraints

import lang.taxi.accessors.Argument
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.Type

/**
 * An explicit instance, modelled as an argument (named 'this'), exposed so it can be used
 * within expressions.
 * Currently, used for modelling operation contracts (both return types and params).
 * We could've used ProjectionFunctionScope, but felt that was misleading in terms of name - though
 * they're both functionally the same, and may merge at a later point
 */
data class InstanceArgument(override val type: Type, override val name: String = ProjectionFunctionScope.THIS) : Argument {
   override fun pruneFieldPath(path: List<String>): List<String> {
      // The first identifier is the name of the scope, and doesn't
      // need to be resolved (eg: "this")
      return path.drop(1)
   }
   override fun pruneFieldSelectors(fieldSelectors: List<FieldReferenceSelector>): List<FieldReferenceSelector> {
      // The first identifier is the name of the scope, and doesn't
      // need to be resolved (eg: "this")
      return fieldSelectors.drop(1)
   }
}
