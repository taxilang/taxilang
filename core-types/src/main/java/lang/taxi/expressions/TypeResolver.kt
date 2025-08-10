package lang.taxi.expressions

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.types.ArrayType
import lang.taxi.types.FormulaOperator
import lang.taxi.types.GenericType
import lang.taxi.types.MapType
import lang.taxi.types.NumberTypes
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.utils.log

/**
 * The TypeResolver is responsible for determining the result type of expressions in Taxi.
 *
 * It supports resolving:
 * - Arithmetic operations (`+`, `-`, `*`, `/`, `%`, etc.)
 * - Elvis (null-coalescing) operations (`?:`)
 * - Type inference for generic and parameterized types (e.g., `List<A> ?: List<B>`)
 * - Least upper bound (LUB) resolution across nominal types
 *
 * === Resolution Strategy ===
 *
 * 1. Arithmetic Operations
 * -------------------------
 * For arithmetic operators (`+`, `-`, `*`, `%`, etc.):
 *   - If all operand types are numeric:
 *       - Use numeric promotion rules to determine the result type.
 *       - The highest-precision numeric type among the operands is selected:
 *         Decimal > Double > Float > Long > Int
 *
 *   - Division (`/`) is treated specially:
 *       - If any operand is a Decimal → result is Decimal
 *       - Otherwise → result is Double (to preserve fractional result)
 *
 *   - Mixed numeric and non-numeric types → result is `Any` or a type error depending on context.
 *
 *
 * 2. Elvis Operator (`?:`)
 * -------------------------
 * Resolves the least upper bound (LUB) of the two operands.
 *
 *   - If both operands have the same type → result is that type.
 *   - If one is a subtype of the other → result is the supertype.
 *   - If both inherit from a common parent → result is that common type.
 *   - If unrelated → result is `Any`.
 *
 * For parameterized types (e.g., `List<A>` and `List<B>`):
 *   - If outer types match → result is List<LUB(A, B)>
 *   - If outer types differ but share a parent (e.g., Array<T> and Stream<T>) → result is the parent with inferred type param
 *   - Otherwise → result is `Any`
 *
 *
 * 3. Nullability Handling
 * -------------------------
 * Nullability is preserved conservatively:
 *   - If any operand is nullable, the result is typically nullable (unless the operator guarantees non-null).
 *
 *
 * 4. Generic Type Promotion
 * -------------------------
 * For types like `Map<K,V>` and `List<T>`, the outer type must match or have a common ancestor.
 *   - Matching outer types: unify each type argument via LUB
 *   - Mismatched outer types: fall back to `Any`
 *
 * === Examples ===
 *   - Int + Double => Double
 *   - Int / Int => Decimal
 *   - A[] ?: B[] => C[] if A and B inherit C; else Any[]
 *   - Map<String, A> ?: Map<String, B> => Map<String, C>
 *
 * This class is central to expression compilation, and all inference rules are expected to be sound and complete.
 */
class TypeResolver {
   companion object {
      fun getCommonType(lhsType: Type, operator: FormulaOperator, rhsType: Type): Either<String, Type> {
         return when {
            operator.isComparisonOperator() -> PrimitiveType.BOOLEAN.right()
            isGenericType(lhsType) && isGenericType(rhsType) -> getCommonGenericType(lhsType, operator, rhsType)
            lhsType.isScalar && rhsType.isScalar -> getCommonScalar(lhsType, operator, rhsType)
            else ->PrimitiveType.ANY.right() // error("Unhandled type resolver scenario: $lhsType $operator $rhsType")
         }
      }

      private fun getCommonGenericType(lhsType: Type, operator: FormulaOperator, rhsType: Type): Either<String, Type> {
         // TODO : We need to consider if the base type of the generic is the same
         // But we don't have good support for sub-types of Array, Map or Stream, so let's just set up a
         // trap for now..
         val expectedGenericTypes = setOf(ArrayType.qualifiedName, StreamType.qualifiedName, MapType.qualifiedName)
         if (lhsType.toQualifiedName() !in expectedGenericTypes) {
            log().error("Unexpected generic type ${lhsType::class.simpleName} - type resolution may not behave as expected")
         }
         if (rhsType.toQualifiedName() !in expectedGenericTypes) {
            log().error("Unexpected generic type ${lhsType::class.simpleName} - type resolution may not behave as expected")
         }
         if (lhsType !is GenericType) {
            return "Expected a generic type, but found ${lhsType.javaClass.simpleName}".left()
         }
         if (rhsType !is GenericType) {
            return "Expected a generic type, but found ${rhsType.javaClass.simpleName}".left()
         }
         // Intentionally looking at the qualified name, not the parameterzied name here,
         // as we're checking for Array == Array, and not Array<T> == Array<T>
         return if (lhsType.toQualifiedName().fullyQualifiedName == rhsType.toQualifiedName().fullyQualifiedName) {
            if (lhsType.typeParameters().size != rhsType.typeParameters().size) {
               PrimitiveType.ANY.right()
            } else {
               val resolvedTypeParameters = lhsType.typeParameters().mapIndexed { index, lhsParameterType ->
                  val rhsParameterType = rhsType.typeParameters()[index]
                  when (val resolvedType = getCommonType(lhsParameterType,operator,rhsParameterType)) {
                     is Either.Right -> resolvedType.value
                     else ->  return resolvedType
                  }
               }
               lhsType.withParameters(resolvedTypeParameters)
                  .mapLeft { error -> error.message }
            }
         } else {
            PrimitiveType.ANY.right()
         }
      }

      /**
       * Finds the lowest common type (least upper bound) among a collection of types.
       * This leverages the existing closestCommonType logic and type resolution rules.
       *
       * @param types The collection of types to find the common type for
       * @return The lowest common type, or PrimitiveType.ANY if no common type exists
       */
      fun findLowestCommonType(types: List<Type>): Type {
         if (types.isEmpty()) return PrimitiveType.ANY
         if (types.size == 1) return types.first()

         // Use the existing elvis operator logic to find common types
         // by reducing the list using the coalesce operator rules
         return types.reduce { acc, type ->
            getCommonType(acc, FormulaOperator.Coalesce, type).fold(
               ifLeft = { PrimitiveType.ANY },
               ifRight = { it }
            )
         }
      }

      private fun isGenericType(type: Type): Boolean = type.typeParameters().isNotEmpty()

      private fun closestCommonType(lhsType: Type, rhsType: Type, lowestCommonPrimitive: PrimitiveType): Type {
         val lhsTree = listOf(lhsType) + lhsType.allInheritedTypes
         val rhsTree = listOf(rhsType) + rhsType.allInheritedTypes
         val commonTypes = lhsTree.filter { rhsTree.contains(it) }
            // Exclude low common types, as we'd prefer lowestCommonPrimitive over these
            .filter { it != PrimitiveType.ANY && it != PrimitiveType.NOTHING }
         return if (commonTypes.isEmpty()) {
            lowestCommonPrimitive
         } else {
            commonTypes.first()
         }
      }
      private fun getCommonScalar(lhsType: Type, operator: FormulaOperator, rhsType: Type): Either<String, Type> {
         if (lhsType.basePrimitive == null) return "LHS type is scalar, but does not declare a basePrimitive".left()
         if (rhsType.basePrimitive == null) return "RHS type is scalar, but does not declare a basePrimitive".left()
         val lhsPrimitive = lhsType.basePrimitive!!
         val rhsPrimitive = rhsType.basePrimitive!!
         return when (operator) {
            // ── Arithmetic (except /) ────────────────────────────────────────────────
            FormulaOperator.Add,
            FormulaOperator.Subtract,
            FormulaOperator.Multiply,
            FormulaOperator.Modulo -> {
               // Numeric promotion
               if (PrimitiveType.isNumberType(lhsType) && PrimitiveType.isNumberType(rhsType)) {
                  NumberTypes.getTypeWithHighestPrecision(setOf(lhsPrimitive, rhsPrimitive)).right()
               }

               else if (operator == FormulaOperator.Add &&
                  (lhsPrimitive == PrimitiveType.STRING || rhsPrimitive == PrimitiveType.STRING)) {
                  closestCommonType(lhsType, rhsType, PrimitiveType.STRING).right()
               }
               // LocalDate + Time ⇒ LocalDateTime (Taxi-specific convenience)
               else if (operator == FormulaOperator.Add &&
                  ((lhsPrimitive == PrimitiveType.LOCAL_DATE && rhsPrimitive == PrimitiveType.TIME) ||
                     (lhsPrimitive == PrimitiveType.TIME && rhsPrimitive == PrimitiveType.LOCAL_DATE))) {
                  PrimitiveType.INSTANT.right()
               }
               else {
                  // Fallback
                  closestCommonType(lhsType, rhsType, PrimitiveType.ANY).right()
               }
            }
            // ── Division (/) ─────────────────────────────────────────────────────────
            FormulaOperator.Divide -> {
               val primitiveType = if (PrimitiveType.isNumberType(lhsType) && PrimitiveType.isNumberType(rhsType)) {
                  if (lhsType == PrimitiveType.DOUBLE || rhsType == PrimitiveType.DOUBLE)
                     PrimitiveType.DOUBLE
                  else
                     PrimitiveType.DECIMAL
               } else {
                  PrimitiveType.ANY
               }
               // We can't use closestCommonType here, (at least, not without some more deep thought),
               // as there's additional rules at play.
               // Given Int / Int = Decimal, T : Int / T : Int = Decimal.
               // In theory, T : Decimal / T : Decimal could = Decimal.
               // That's for later thought
               primitiveType.right()
//               closestCommonType(lhsType,rhsType, primitiveType).right()
            }
            // ── Elvis (?:) ───────────────────────────────────────────────────────────
            FormulaOperator.Coalesce -> {
               // numeric–numeric ⇒ numeric LUB
               val primitiveType = if (PrimitiveType.isNumberType(lhsPrimitive) && PrimitiveType.isNumberType(rhsPrimitive)) {
                 NumberTypes.getTypeWithHighestPrecision(setOf( lhsPrimitive, rhsPrimitive))
               }
               // TODO : There are possibly other use-cases to consider here.
               // But typeof A == typeof B is already handled higher up
               else {
                  PrimitiveType.ANY
               }
               closestCommonType(lhsType,rhsType, primitiveType).right()
            }
            // ── Logical ops ─────────────────────────────────────────────────────────

            FormulaOperator.LogicalAnd,
            FormulaOperator.LogicalOr -> PrimitiveType.BOOLEAN.right()

            // ── Comparison / Equality ───────────────────────────────────────────────
            FormulaOperator.GreaterThan,
            FormulaOperator.GreaterThanOrEqual,
            FormulaOperator.LessThan,
            FormulaOperator.LessThanOrEqual,
            FormulaOperator.Equal,
            FormulaOperator.NotEqual -> PrimitiveType.BOOLEAN.right()

            // ── Fallback ────────────────────────────────────────────────────────────
            else -> closestCommonType(lhsType,rhsType,PrimitiveType.ANY).right()
         }
      }
   }


}
