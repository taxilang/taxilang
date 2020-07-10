package lang.taxi.types

/**
 * A set of fields with additional conditional mapping logic
 * applied
 */
data class ConditionalFieldSet(val fields: List<Field>, val condition: FieldSetCondition)

interface FieldSetCondition

data class WhenFieldSetCondition(
   val selectorExpression: WhenSelectorExpression,
   val cases: List<WhenCaseBlock>
) : FieldSetCondition

interface WhenSelectorExpression

data class AccessorExpressionSelector(
   val accessor: Accessor,
   val declaredType: Type
) : WhenSelectorExpression

data class FieldReferenceSelector(val fieldName: String) : WhenSelectorExpression

data class WhenCaseBlock(
   val matchExpression: WhenCaseMatchExpression,
   val assignments: List<AssignmentExpression>
) {
   fun getAssignmentFor(fieldName: String): FieldAssignmentExpression {
      return assignments
         .filterIsInstance<FieldAssignmentExpression>()
         .firstOrNull { it.fieldName == fieldName } ?: error("No assignment exists for field $fieldName")
   }

   /**
    * Expects that a single InlineAssignmentExpression is present,
    * and will error if not.
    */
   fun getSingleAssignment(): InlineAssignmentExpression {
      require(assignments.size == 1) { "Cannot call getSingleAssignment() when there are multiple assignments present.  You should call getAssignmentFor(..) instead"}
      require(assignments[0] is InlineAssignmentExpression ) { "Expected single assignment to be an InlineAssignmentExpression, but found ${assignments[0]::class.simpleName}"}
      return assignments[0] as InlineAssignmentExpression
   }
}

sealed class AssignmentExpression {
   abstract val assignment: ValueAssignment
}

/**
 * An assignment for a specific field.
 * Used when mapping a when { } block for mulitple fields, to indicate which field is going to
 * be assigned
 */
data class FieldAssignmentExpression(val fieldName: String, override val assignment: ValueAssignment) : AssignmentExpression()

/**
 * An assignment where the field is implied
 * Used when mapping a when block for a single field, and the field name is handled higher
 * in the AST.
 */
data class InlineAssignmentExpression(override val assignment: ValueAssignment) : AssignmentExpression()


interface WhenCaseMatchExpression
class ReferenceCaseMatchExpression(val reference: String) : WhenCaseMatchExpression
class LiteralCaseMatchExpression(val value: Any) : WhenCaseMatchExpression
object ElseMatchExpression : WhenCaseMatchExpression

interface ValueAssignment
data class ScalarAccessorValueAssignment(val accessor: Accessor) : ValueAssignment
data class DestructuredAssignment(val assignments: List<FieldAssignmentExpression>) : ValueAssignment
data class ReferenceAssignment(val reference: String) : ValueAssignment
data class LiteralAssignment(val value: Any) : ValueAssignment
data class EnumValueAssignment(val enum:EnumType, val enumValue: EnumValue) : ValueAssignment
object NullAssignment : ValueAssignment
