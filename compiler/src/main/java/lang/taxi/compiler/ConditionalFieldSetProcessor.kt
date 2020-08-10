package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.right
import lang.taxi.*
import lang.taxi.types.*
import org.antlr.v4.runtime.RuleContext

class ConditionalFieldSetProcessor internal constructor(private val compiler: TokenProcessor) {
   fun compileConditionalFieldStructure(fieldBlock: TaxiParser.ConditionalTypeStructureDeclarationContext, namespace: Namespace): Either<CompilationError, ConditionalFieldSet> {
      return compileCondition(fieldBlock.conditionalTypeConditionDeclaration(), namespace).map { condition ->
         val fields = fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
            compiler.compiledField(fieldDeclaration, namespace)?.copy(readExpression = condition)
         }

         ConditionalFieldSet(fields, condition)
      }
   }

   fun compileCondition(conditionDeclaration: TaxiParser.ConditionalTypeConditionDeclarationContext, namespace: Namespace): Either<CompilationError, FieldSetExpression> {
      return when {
         conditionDeclaration.conditionalTypeWhenDeclaration() != null -> compileWhenCondition(conditionDeclaration.conditionalTypeWhenDeclaration(), namespace)
         conditionDeclaration.fieldExpression() != null -> compileFieldExpression(conditionDeclaration.fieldExpression(), namespace)
         else -> error("Unhandled condition type")
      }
   }

   private fun compileFieldExpression(fieldExpression: TaxiParser.FieldExpressionContext, namespace: Namespace): Either<CompilationError, FieldSetExpression> {
      val operand1 = fieldExpression.propertyToParameterConstraintLhs(0).qualifiedName().text
      val operand2 = fieldExpression.propertyToParameterConstraintLhs(1).qualifiedName().text
      val operand1FieldReferenceSelector =  FieldReferenceSelector(operand1)
      val operand2FieldReferenceSelector = FieldReferenceSelector(operand2)
      val operator = FormulaOperator.forSymbol(fieldExpression.arithmaticOperator().text)
      val typeDeclarationContext = getTypeDeclarationContext(fieldExpression)
              ?: return Either.left(CompilationError(fieldExpression.start, "Cannot resolve enclosing type for ${fieldExpression.text}"))
      val operandNameTypeMap = typeDeclarationContext.typeBody().typeMemberDeclaration().map { it.fieldDeclaration().Identifier().text to it.fieldDeclaration().typeType()}.toMap()
      operandNameTypeMap[operand1] ?: return Either.left(CompilationError(fieldExpression.start, "Cannot find attribute of $operand1"))
      operandNameTypeMap[operand2] ?: return Either.left(CompilationError(fieldExpression.start, "Cannot find attribute of $operand2"))

      return CalculatedFieldSetExpression(operand1FieldReferenceSelector, operand2FieldReferenceSelector, operator).right()
   }

   private fun getTypeDeclarationContext(parserRuleContext: RuleContext?): TaxiParser.TypeDeclarationContext? {
      return when {
         parserRuleContext is TaxiParser.TypeDeclarationContext -> parserRuleContext
         parserRuleContext?.parent != null -> getTypeDeclarationContext(parserRuleContext.parent)
         else -> null
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
      val assignments = when {
         whenCase.caseFieldAssignmentBlock() != null -> {
            whenCase.caseFieldAssignmentBlock().caseFieldAssigningDeclaration().map {
               compileFieldAssignment(it)
            }
         }
         whenCase.caseScalarAssigningDeclaration() != null -> {
            listOf(compileScalarFieldAssignment(whenCase.caseScalarAssigningDeclaration()))
         }
         else -> error("Unhandled when case branch")
      }
      return WhenCaseBlock(matchExpression, assignments)
   }

   private fun compileScalarFieldAssignment(scalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext): InlineAssignmentExpression {
      val assignment: ValueAssignment = when {
         scalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(scalarAssigningDeclaration.literal())
         scalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(scalarAssigningDeclaration.caseFieldReferenceAssignment())
         scalarAssigningDeclaration.scalarAccessorExpression() != null -> {
            TODO("Processing of xpath(..) or jsonPath(..) in a when block is not yet implemented, but it should be.")
         }
         else -> error("Unhandled scalar value assignment")
      }
      return InlineAssignmentExpression(assignment)
   }

   private fun compileFieldAssignment(caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext): FieldAssignmentExpression {
      val assignment: ValueAssignment = when {
         caseFieldAssignment.caseFieldDestructuredAssignment() != null -> compileDestructuredValueAssignment(caseFieldAssignment.caseFieldDestructuredAssignment())
         caseFieldAssignment.caseScalarAssigningDeclaration() != null -> compileCaseScalarAssignment(caseFieldAssignment.caseScalarAssigningDeclaration())
         caseFieldAssignment.scalarAccessor() != null -> compileScalarAccessorValueAssignment(caseFieldAssignment.scalarAccessor())
         else -> error("Unhandled object field value assignment")
      }
      return FieldAssignmentExpression(caseFieldAssignment.Identifier().text, assignment)
   }

   private fun compileCaseScalarAssignment(caseScalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext): ValueAssignment {
      return when {
         caseScalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(caseScalarAssigningDeclaration.caseFieldReferenceAssignment())
         caseScalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(caseScalarAssigningDeclaration.literal())
         else -> error("Unhandled case scalar assignment")
      }


   }

   private fun compileScalarAccessorValueAssignment(scalarAccessor: TaxiParser.ScalarAccessorContext): ScalarAccessorValueAssignment {
      val accessor = compiler.compileScalarAccessor(scalarAccessor)
      return ScalarAccessorValueAssignment(accessor)
   }

   private fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): ValueAssignment {
      return if (literal.valueOrNull() == null) {
         NullAssignment
      } else {
         LiteralAssignment(literal.value())
      }

   }

   private fun compileReferenceValueAssignment(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): ValueAssignment {
      return if (caseFieldReferenceAssignment.Identifier().size > 1) {
         // This is a Foo.Bar -- lets check to see if we can resolve this as an enum
         compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment)
      } else {
         ReferenceAssignment(caseFieldReferenceAssignment.text)
      }
   }

   private fun compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): ValueAssignment {
      val enumName = caseFieldReferenceAssignment.Identifier().dropLast(1).joinToString(".")
      val typeName = compiler.lookupTypeByName(enumName, caseFieldReferenceAssignment)
      val enumReference = compiler.typeResolver(caseFieldReferenceAssignment.findNamespace()).resolve(typeName, caseFieldReferenceAssignment).flatMap { type ->
         require(type is EnumType) { "Expected $typeName to be an enum" }
         val enumType = type as EnumType// for readability
         val enumReference = caseFieldReferenceAssignment.Identifier().last()
         if (enumType.has(enumReference.text)) {
            Either.right(EnumValueAssignment(enumType, type.of(enumReference.text)))
         } else {
            Either.left(CompilationError(caseFieldReferenceAssignment.start, "Cannot resolve EnumValue of ${caseFieldReferenceAssignment.Identifier().text()}"))
         }
         // TODO : MOdify the return type to handle eithers
      }.getOrHandle { error -> throw CompilationException(error) }
      return enumReference
   }

   private fun compileDestructuredValueAssignment(caseFieldDestructuredAssignment: TaxiParser.CaseFieldDestructuredAssignmentContext): ValueAssignment {
      return DestructuredAssignment(caseFieldDestructuredAssignment.caseFieldAssigningDeclaration().map { compileFieldAssignment(it) })
   }

   private fun compileMatchExpression(caseDeclarationMatchExpression: TaxiParser.CaseDeclarationMatchExpressionContext): WhenCaseMatchExpression {
      return when {
         caseDeclarationMatchExpression.Identifier() != null -> ReferenceCaseMatchExpression(caseDeclarationMatchExpression.Identifier().text)
         caseDeclarationMatchExpression.literal() != null -> LiteralCaseMatchExpression(caseDeclarationMatchExpression.literal().value())
         caseDeclarationMatchExpression.caseElseMatchExpression() != null -> ElseMatchExpression
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

