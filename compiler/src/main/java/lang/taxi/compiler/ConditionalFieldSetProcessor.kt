package lang.taxi.compiler

import arrow.core.Either
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.types.*
import lang.taxi.value

class ConditionalFieldSetProcessor internal constructor(private val compiler: TokenProcessor) {
   fun compileConditionalFieldStructure(fieldBlock: TaxiParser.ConditionalTypeStructureDeclarationContext, namespace: Namespace): Either<CompilationError, ConditionalFieldSet> {
      return compileCondition(fieldBlock.conditionalTypeConditionDeclaration(), namespace).map { condition ->
         val fields = fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
            compiler.compiledField(fieldDeclaration, namespace)?.copy(readCondition = condition)
         }

         ConditionalFieldSet(fields, condition)
      }
   }

   private fun compileCondition(conditionDeclaration: TaxiParser.ConditionalTypeConditionDeclarationContext, namespace: Namespace): Either<CompilationError, WhenFieldSetCondition> {
      return when {
         conditionDeclaration.conditionalTypeWhenDeclaration() != null -> compileWhenCondition(conditionDeclaration.conditionalTypeWhenDeclaration(), namespace)
         else -> error("Unhandled condition type")
      }
   }

   private fun compileWhenCondition(whenBlock: TaxiParser.ConditionalTypeWhenDeclarationContext, namespace: Namespace): Either<CompilationError, WhenFieldSetCondition> {
      return compileSelectorExpression(whenBlock.conditionalTypeWhenSelector(), namespace).map { selectorExpression ->
         val cases = compileWhenCases(whenBlock.conditionalTypeWhenCaseDeclaration())
         WhenFieldSetCondition(selectorExpression, cases)
      }
   }

   private fun compileWhenCases(conditionalTypeWhenCaseDeclaration: List<TaxiParser.ConditionalTypeWhenCaseDeclarationContext>): List<WhenCaseBlock> {
      return conditionalTypeWhenCaseDeclaration.map { compileWhenCase(it) }
   }

   private fun compileWhenCase(whenCase: TaxiParser.ConditionalTypeWhenCaseDeclarationContext): WhenCaseBlock {
      val matchExpression = compileMatchExpression(whenCase.caseDeclarationMatchExpression())
      val assignments = whenCase.caseFieldAssigningDeclaration().map {
         compileFieldAssignment(it)
      }
      return WhenCaseBlock(matchExpression, assignments)
   }

   private fun compileFieldAssignment(caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext): CaseFieldAssignmentExpression {
      val assignment: ValueAssignment = when {
         caseFieldAssignment.caseFieldDestructuredAssignment() != null -> compileDestructuredValueAssignment(caseFieldAssignment.caseFieldDestructuredAssignment())
         caseFieldAssignment.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(caseFieldAssignment.caseFieldReferenceAssignment())
         caseFieldAssignment.literal() != null -> compileLiteralValueAssignment(caseFieldAssignment.literal())
         caseFieldAssignment.scalarAccessor() != null -> compileScalarAccessorValueAssignment(caseFieldAssignment.scalarAccessor())
         else -> error("Unhandled value assignment")
      }
      return CaseFieldAssignmentExpression(caseFieldAssignment.Identifier().text, assignment)
   }

   private fun compileScalarAccessorValueAssignment(scalarAccessor: TaxiParser.ScalarAccessorContext): ScalarAccessorValueAssignment {
      val accessor = compiler.compileScalarAccessor(scalarAccessor)
      return ScalarAccessorValueAssignment(accessor)
   }

   private fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): LiteralAssignment {

      return LiteralAssignment(literal.value())

   }

   private fun compileReferenceValueAssignment(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): ValueAssignment {
      return ReferenceAssignment(caseFieldReferenceAssignment.text)
   }

   private fun compileDestructuredValueAssignment(caseFieldDestructuredAssignment: TaxiParser.CaseFieldDestructuredAssignmentContext): ValueAssignment {
      return DestructuredAssignment(caseFieldDestructuredAssignment.caseFieldAssigningDeclaration().map { compileFieldAssignment(it) })
   }

   private fun compileMatchExpression(caseDeclarationMatchExpression: TaxiParser.CaseDeclarationMatchExpressionContext): WhenCaseMatchExpression {
      return when {
         caseDeclarationMatchExpression.Identifier() != null -> ReferenceCaseMatchExpression(caseDeclarationMatchExpression.Identifier().text)
         caseDeclarationMatchExpression.literal() != null -> LiteralCaseMatchExpression(caseDeclarationMatchExpression.literal().value())
         else -> error("Unhandled case match expression")
      }
   }

   private fun compileSelectorExpression(selectorBlock: TaxiParser.ConditionalTypeWhenSelectorContext, namespace: Namespace): Either<CompilationError, WhenSelectorExpression> {
      return when {
         selectorBlock.mappedExpressionSelector() != null -> compileTypedAccessor(selectorBlock.mappedExpressionSelector(), namespace)
         selectorBlock.fieldReferenceSelector() != null -> compileFieldReferenceSelector(selectorBlock.fieldReferenceSelector())
         else -> error("Unhandled where block selector condition")
      }

   }

   private fun compileFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<CompilationError, WhenSelectorExpression> {
      return FieldReferenceSelector(fieldReferenceSelector.text).right()
   }

   private fun compileTypedAccessor(expressionSelector: TaxiParser.MappedExpressionSelectorContext, namespace: Namespace): Either<CompilationError, AccessorExpressionSelector> {
      val accessor = compiler.compileScalarAccessor(expressionSelector.scalarAccessorExpression())
      return compiler.parseType(namespace, expressionSelector.typeType()).map { type ->
         AccessorExpressionSelector(accessor, type)
      }
   }
}

