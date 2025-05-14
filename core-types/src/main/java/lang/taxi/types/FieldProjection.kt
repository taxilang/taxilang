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

   /**
    * The actual return type of the projection.
    * Generally this is the same as the proejcted type, unless the projection is
    * operating on a stream, in which case it's Stream<T>, not T[]
    */
   val returnType = convertToStreamIfStreamType(sourceType,projectedType)

   companion object {
      /**
       * If the source of the projection is a Stream<A>, then the return type is actually Stream<B>,
       * not B[].
       */
      // This might not be the "perfect" place to do this, but it needs to happen - happy to move it.
      private fun convertToStreamIfStreamType(sourceType: Type, projectedType: Type): Type {
         if (StreamType.isStream(sourceType)) {
            // Historically, streams have always been declared as
            // stream { A } as B[], as the response is iterable.
            // The response is Stream<B>, not Stream<B[]>
            // That might not be right ... if not, we can revisit this logic.
            val unwrappedProjectedType = Arrays.unwrapPossibleArrayType(projectedType)
            return StreamType.of(unwrappedProjectedType, projectedType.compilationUnits.firstOrNull() ?: CompilationUnit.unspecified())
         } else return projectedType
      }
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
