package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * Checks structural compatibility between types using duck typing principles.
 *
 * Structural compatibility rules:
 * 1. Type A is structurally compatible with type B if A has all fields that B requires
 *    (A may have additional fields - duck typing/superset allowed)
 *
 * 2. Field compatibility depends on field nature:
 *    - Scalar fields (primitives, semantic types): Use nominal assignability
 *      (field.type.isAssignableTo(requiredField.type))
 *    - Object fields (models, nested structures): Use recursive structural compatibility
 *      (field.type.isStructurallyCompatibleWith(requiredField.type))
 *    - Array fields: Check element types recursively using the same rules
 *
 * 3. Structural compatibility is checked AFTER nominal typing in isAssignableTo:
 *    - Nominal typing takes precedence (faster, more precise)
 *    - Structural compatibility acts as a fallback before final inheritsFrom check
 *
 * Example - Structurally compatible:
 *   model Person { name: PersonName, age: Age }
 *   model EnrichedPerson { name: PersonName, age: Age, email: Email }
 *
 *   EnrichedPerson is structurally compatible with Person (has all required fields plus extras)
 *
 * Example - NOT structurally compatible (semantic mismatch at field level):
 *   type PersonName inherits String
 *   type DogName inherits String
 *
 *   model Person { name: PersonName }
 *   model Dog { name: DogName }
 *
 *   Dog is NOT structurally compatible with Person, even though both have a 'name' field,
 *   because DogName is not nominally assignable to PersonName (different semantic types)
 */
object StructuralTypeChecker {
   val IsStructurallyCompatible: Either<String, Boolean> = true.right()
   fun structuralCompatibilityApplies(valueType: Type, receiverType: Type): Boolean {
      // Neither type can be scalar for structural compatibility to apply
      if (valueType.isScalar || receiverType.isScalar) return false
      // Other reasons?
      return true
   }

   fun isStructurallyCompatible(valueType: Type, receiverType: Type): Boolean {
      return structurallyCompatibleOrErrors(valueType, receiverType)
         .isRight()
   }

   fun structurallyCompatibleOrErrors(valueType: Type, receiverType: Type): Either<String, Boolean> {
      // Only object types can be structurally compared
      fun failWithReason(reason: String): Either<String, Boolean> {
         return "${valueType.toQualifiedName().shortDisplayName} is not structurally compatible with ${receiverType.toQualifiedName().shortDisplayName} because $reason".left()
      }
      if (valueType !is ObjectType || receiverType !is ObjectType) {
         return failWithReason("both types must be objects to be structurally compatible")
      }

      // Scalars vs non-scalars can't be structurally compared
      if (valueType.isScalar != receiverType.isScalar) {
         return failWithReason("a mismatch of scalar types")
      }

      // Duck typing: valueType must have all fields that receiverType requires
      // (valueType can have additional fields)
      val missingOrIncompatibleFields = receiverType.fields.mapNotNull { requiredField ->
         val matchingField = valueType.fields.find { it.name == requiredField.name }

         when {
            matchingField == null ->
               "Missing required field '${requiredField.name}': ${requiredField.type.qualifiedName}'"
                  .left()

            else -> {
               isFieldTypeCompatible(requiredField.name, matchingField.type, requiredField.type)
            }
         }
      }.filter { it.isLeft() }

      return if (missingOrIncompatibleFields.isEmpty()) {
         IsStructurallyCompatible
      } else {
         val problems = missingOrIncompatibleFields.map {
            (it as Either.Left).value
         }
         failWithReason(problems.joinToString(", "))
      }
   }

   private fun isFieldTypeCompatible(
      fieldName: String,
      valueFieldType: Type,
      receiverType: Type
   ): Either<String, Boolean> {
      return if (valueFieldType.isAssignableTo(receiverType)) {
         IsStructurallyCompatible
      } else {
         "Field '$fieldName' is has incompatible types of ${valueFieldType.toQualifiedName().shortDisplayName} and ${receiverType.toQualifiedName().shortDisplayName}"
            .left()
      }

//      return when {
//         // Scalar types use nominal assignability
//         valueFieldType.isScalar && receiverType.isScalar -> {
//            if (valueFieldType.isAssignableTo(receiverType)) {
//               IsStructurallyCompatible
//            } else {
//               "Type ${valueFieldType.qualifiedName} is not assignable to ${receiverType.qualifiedName}".left()
//            }
//         }
//
//         // Object types use recursive structural compatibility
//         valueFieldType is ObjectType && receiverType is ObjectType ->
//            structurallyCompatibleOrErrors(valueFieldType, receiverType)
//
//         // Arrays: check element types recursively
//         Arrays.isArray(valueFieldType) && Arrays.isArray(receiverType) -> {
//            val elementCompatibility = isFieldTypeCompatible(
//               Arrays.unwrapPossibleArrayType(valueFieldType),
//               Arrays.unwrapPossibleArrayType(receiverType)
//            )
//            elementCompatibility.mapLeft { error ->
//               "Array element type mismatch: $error"
//            }
//         }
//
//         // Fallback to nominal assignability
//         else -> {
//            if (valueFieldType.isAssignableTo(receiverType)) {
//               IsStructurallyCompatible
//            } else {
//               "Type ${valueFieldType.qualifiedName} is not assignable to ${receiverType.qualifiedName}".left()
//            }
//         }
//      }
   }
}
