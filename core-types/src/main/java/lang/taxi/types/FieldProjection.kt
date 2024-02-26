package lang.taxi.types

import lang.taxi.accessors.ProjectionFunctionScope

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
   val projectedType: Type,
   val projectionFunctionScope: List<ProjectionFunctionScope>
) {
   companion object {
      fun forNullable(sourceType: Type, projectedTypeAndScope: Pair<Type, List<ProjectionFunctionScope>>?): FieldProjection? {
         return if (projectedTypeAndScope == null) {
            null
         } else {
            val (projectedType, scope) = projectedTypeAndScope
            FieldProjection(sourceType, projectedType, scope)
         }
      }
   }
}
