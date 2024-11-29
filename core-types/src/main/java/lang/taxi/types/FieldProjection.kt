package lang.taxi.types

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.services.operations.constraints.Constraint

/**
 * Allows a field to define a projection for the field specifically.
 * ie.,
 * find { ... } as {
 *    foo : Thing as { <--- everything inside here.
 *
 *    }
 *
 * When we're building this, we need both the projected type, and the original
 * source type.  ('Thing' in the above example)
 */
data class FieldProjection(
   val sourceType: Type,
   val sourceTypeConstraints: List<Constraint> = emptyList(),
   val projectedType: Type,
   val projectionFunctionScope: List<ProjectionFunctionScope>
) {
   companion object {
      fun forNullable(
         sourceType: Type,
         sourceTypeConstraints: List<Constraint> = emptyList(),
         projectedTypeAndScope: Pair<Type, List<ProjectionFunctionScope>>?
      ): FieldProjection? {
         return if (projectedTypeAndScope == null) {
            null
         } else {
            val (projectedType, scope) = projectedTypeAndScope
            FieldProjection(sourceType, sourceTypeConstraints,  projectedType, scope)
         }
      }
   }
}
