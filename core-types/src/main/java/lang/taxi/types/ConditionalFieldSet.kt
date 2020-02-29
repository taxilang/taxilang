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
   val assignments: List<CaseFieldAssignmentExpression>
) {
   fun getAssignmentFor(fieldName: String): CaseFieldAssignmentExpression {
      return assignments.firstOrNull { it.fieldName == fieldName } ?: error("No assignment exists for field $fieldName")
   }
}

data class CaseFieldAssignmentExpression(val fieldName: String, val assignment: ValueAssignment)

interface WhenCaseMatchExpression
class ReferenceCaseMatchExpression(val reference: String):WhenCaseMatchExpression
class LiteralCaseMatchExpression(val value:Any):WhenCaseMatchExpression

interface ValueAssignment
data class ScalarAccessorValueAssignment(val accessor: Accessor) : ValueAssignment
data class DestructuredAssignment(val assignments: List<CaseFieldAssignmentExpression>) : ValueAssignment
data class ReferenceAssignment(val reference:String):ValueAssignment
data class LiteralAssignment(val value:Any):ValueAssignment

