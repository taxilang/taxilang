package lang.taxi.types

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
   val projectedType: Type
)
