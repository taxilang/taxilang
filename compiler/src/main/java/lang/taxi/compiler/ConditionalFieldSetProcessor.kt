package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.CompilationException
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.text
import lang.taxi.types.AccessorExpressionSelector
import lang.taxi.types.AndExpression
import lang.taxi.types.CalculatedFieldSetExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConditionalFieldSet
import lang.taxi.types.ConstantEntity
import lang.taxi.types.DestructuredAssignment
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.EnumLiteralCaseMatchExpression
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.EnumValueAssignment
import lang.taxi.types.FieldAssignmentExpression
import lang.taxi.types.FieldReferenceEntity
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FieldSetExpression
import lang.taxi.types.FormulaOperator
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.LiteralCaseMatchExpression
import lang.taxi.types.LogicalConstant
import lang.taxi.types.LogicalExpression
import lang.taxi.types.LogicalVariable
import lang.taxi.types.NullAssignment
import lang.taxi.types.OrExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.ReferenceAssignment
import lang.taxi.types.ReferenceCaseMatchExpression
import lang.taxi.types.ScalarAccessorValueAssignment
import lang.taxi.types.Type
import lang.taxi.types.ValueAssignment
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenCaseMatchExpression
import lang.taxi.types.WhenFieldSetCondition
import lang.taxi.types.WhenSelectorExpression
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.value
import lang.taxi.valueOrNull
import org.antlr.v4.runtime.RuleContext

class ConditionalFieldSetProcessor internal constructor(private val compiler: FieldCompiler) {
   fun compileConditionalFieldStructure(fieldBlock: TaxiParser.ConditionalTypeStructureDeclarationContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, ConditionalFieldSet> {
//      val fields = fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
//         compiler.compiledField(fieldDeclaration, namespace)?.let { field ->
//            compileCondition(fieldBlock.conditionalTypeConditionDeclaration(), namespace).map { condition ->
//               field.copy(readExpression = condition)
//            }
//         }
//      }.invertEitherList()
//         .map { fields ->  }


      return compileCondition(fieldBlock.conditionalTypeConditionDeclaration(), namespace, targetType).flatMap { condition ->
         fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
            val fieldName = fieldDeclaration.fieldDeclaration().Identifier().text
            compiler.provideField(fieldName, fieldDeclaration)
               .map { field -> field.copy(readExpression = condition) }

         }.invertEitherList().flattenErrors().map { fields ->
            ConditionalFieldSet(fields, condition)
         }


      }
   }

   fun compileCondition(conditionDeclaration: TaxiParser.ConditionalTypeConditionDeclarationContext, namespace: Namespace, targetType:Type): Either<List<CompilationError>, FieldSetExpression> {
      return when {
         conditionDeclaration.conditionalTypeWhenDeclaration() != null -> compileWhenCondition(conditionDeclaration.conditionalTypeWhenDeclaration(), namespace, targetType)
         conditionDeclaration.fieldExpression() != null -> compileFieldExpression(conditionDeclaration.fieldExpression(), namespace).wrapErrorsInList()
         else -> error("Unhandled condition type")
      }
   }

   private fun compileFieldExpression(fieldExpression: TaxiParser.FieldExpressionContext, namespace: Namespace): Either<CompilationError, FieldSetExpression> {
      val operand1 = fieldExpression.propertyToParameterConstraintLhs(0).qualifiedName().text
      val operand2 = fieldExpression.propertyToParameterConstraintLhs(1).qualifiedName().text
      val operand1FieldReferenceSelector =  FieldReferenceSelector(operand1, PrimitiveType.ANY)
      val operand2FieldReferenceSelector = FieldReferenceSelector(operand2, PrimitiveType.ANY)
      TODO("Gots to sor this out")
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

   private fun compileWhenCondition(whenBlock: TaxiParser.ConditionalTypeWhenDeclarationContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, WhenFieldSetCondition> {
      return compileSelectorExpression(whenBlock.conditionalTypeWhenSelector(), namespace, targetType).map { selectorExpression ->
         val cases = compileWhenCases(whenBlock.conditionalTypeWhenCaseDeclaration(), targetType)
         WhenFieldSetCondition(selectorExpression, cases)
      }
   }

   private fun compileWhenCases(conditionalTypeWhenCaseDeclaration: List<TaxiParser.ConditionalTypeWhenCaseDeclarationContext>, targetType: Type): List<WhenCaseBlock> {
      return conditionalTypeWhenCaseDeclaration.map { compileWhenCase(it, targetType) }
   }

   private fun compileWhenCase(whenCase: TaxiParser.ConditionalTypeWhenCaseDeclarationContext, targetType: Type): WhenCaseBlock {
      val matchExpression = compileMatchExpression(whenCase.caseDeclarationMatchExpression())
      val assignments = when {
         whenCase.caseFieldAssignmentBlock() != null -> {
            whenCase.caseFieldAssignmentBlock().caseFieldAssigningDeclaration().map {
               compileFieldAssignment(it)
            }
         }
         whenCase.caseScalarAssigningDeclaration() != null -> {
            listOf(compileScalarFieldAssignment(whenCase.caseScalarAssigningDeclaration(), targetType))
         }
         else -> error("Unhandled when case branch")
      }
      return WhenCaseBlock(matchExpression, assignments)
   }

   private fun compileScalarFieldAssignment(scalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext, targetType: Type): InlineAssignmentExpression {
      val assignment: ValueAssignment = when {
         scalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(scalarAssigningDeclaration.literal())
         scalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(scalarAssigningDeclaration.caseFieldReferenceAssignment())
         scalarAssigningDeclaration.scalarAccessorExpression() != null -> {
            val accessor = compiler.compileScalarAccessor(scalarAssigningDeclaration.scalarAccessorExpression(), targetType) // TODO : Where do we fine the type info for the field we're compiling?
            ScalarAccessorValueAssignment(accessor)
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
      TODO("Where do we find the type info here?")
      val accessor = compiler.compileScalarAccessor(scalarAccessor, targetType = PrimitiveType.ANY) // TODO : Where do we find the type info for this?
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
         caseDeclarationMatchExpression.enumSynonymSingleDeclaration() != null -> {
            val enumValueQualifiedName =  caseDeclarationMatchExpression.enumSynonymSingleDeclaration().qualifiedName().Identifier().text()
            val (enumTypeName, enumValue) = EnumValue.splitEnumValueName(enumValueQualifiedName)
            val enumRef = compiler.typeResolver(caseDeclarationMatchExpression.findNamespace()).resolve(enumTypeName.fullyQualifiedName, caseDeclarationMatchExpression).flatMap { type ->
               if (type !is EnumType) {
                  Either.left(CompilationError(caseDeclarationMatchExpression.start, "Type ${type.qualifiedName} is not an enum"))
               } else {
                  if (type.has(enumValue)) {
                     Either.right(type.of(enumValue))
                  } else {
                     Either.left(CompilationError(caseDeclarationMatchExpression.start, "'$enumValue' is not defined on enum ${type.qualifiedName}"))
                  }
               }
               // TODO : Fix this to return eithers
            }.getOrHandle { error -> throw CompilationException(error) }
            EnumLiteralCaseMatchExpression(enumRef)
         }

         caseDeclarationMatchExpression.condition() != null -> processLogicalExpressionContext(caseDeclarationMatchExpression.condition().logical_expr()).getOrHandle {
            error -> throw CompilationException(error)
         }

         else -> error("Unhandled case match expression")
      }
   }

   private fun processLogicalExpressionContext(logicalExpressionCtx: TaxiParser.Logical_exprContext): Either<CompilationError, LogicalExpression> {
      return when (logicalExpressionCtx) {
         is TaxiParser.ComparisonExpressionContext -> processComparisonExpressionContext(logicalExpressionCtx.comparison_expr())
         is TaxiParser.LogicalExpressionAndContext -> processLogicalAndContext(logicalExpressionCtx)
         is TaxiParser.LogicalExpressionOrContext -> processLogicalOrContext(logicalExpressionCtx)
         is TaxiParser.LogicalEntityContext -> processLogicalEntityContext(logicalExpressionCtx)
         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
      }
   }

   private fun processLogicalEntityContext(logicalExpressionCtx: TaxiParser.LogicalEntityContext): Either<CompilationError, LogicalExpression> {
      return when (val logicalEntity = logicalExpressionCtx.logical_entity()) {
         is TaxiParser.LogicalVariableContext -> LogicalVariable(logicalEntity.text).right()
         is TaxiParser.LogicalConstContext -> LogicalConstant(logicalEntity.TRUE() != null).right()
         else -> CompilationError(logicalExpressionCtx.start, "invalid logical expression").left()
      }
   }

   private fun processLogicalOrContext(logicalExpressionCtx: TaxiParser.LogicalExpressionOrContext): Either<CompilationError, LogicalExpression> {
      val logicalExpr = logicalExpressionCtx.logical_expr()
      val retVal = logicalExpr.map { processLogicalExpressionContext(it) }
      if (retVal.size != 2) {
         return Either.left(CompilationError(logicalExpressionCtx.start, "invalid and expression"))
      }

      val mapped = retVal.map {
         when (it) {
            is Either.Right -> {
               it.b
            }
            is Either.Left -> {
               return Either.left(CompilationError(logicalExpressionCtx.start, "invalid numeric entity"))
            }
         }
      }
      return OrExpression(mapped[0], mapped[1]).right()
   }

   private fun processLogicalAndContext(logicalExpressionAndCtx: TaxiParser.LogicalExpressionAndContext): Either<CompilationError, LogicalExpression> {
      val logicalExpr = logicalExpressionAndCtx.logical_expr()
      val retVal = logicalExpr.map { processLogicalExpressionContext(it) }
      if (retVal.size != 2) {
         return Either.left(CompilationError(logicalExpressionAndCtx.start, "invalid and expression"))
      }

      val mapped = retVal.map {
         when (it) {
            is Either.Right -> {
               it.b
            }
            is Either.Left -> {
               return Either.left(CompilationError(logicalExpressionAndCtx.start, "invalid numeric entity"))
            }
         }
      }
      return AndExpression(mapped[0], mapped[1]).right()
   }

   private fun processComparisonExpressionContext(comparisonExpressionContext: TaxiParser.Comparison_exprContext): Either<CompilationError, LogicalExpression> {
      val comparisonExpressionContextWithOperator = comparisonExpressionContext as TaxiParser.ComparisonExpressionWithOperatorContext
      val retVal = comparisonExpressionContextWithOperator.comparison_operand().map { comparisonOperandcontext ->
         val arithmeticExpression = comparisonOperandcontext.arithmetic_expr() as TaxiParser.ArithmeticExpressionNumericEntityContext
         when (val numericEntity = arithmeticExpression.numeric_entity()) {
            is TaxiParser.LiteralConstContext -> {
               val str = numericEntity.literal().valueOrNull()
               try {
                  ConstantEntity(str).right()
               } catch (e: Exception) {
                  Either.left(CompilationError(comparisonExpressionContext.start,
                     "$str must be a valid decimal"))

               }

            }
            is TaxiParser.NumericVariableContext -> FieldReferenceEntity(numericEntity.propertyToParameterConstraintLhs().qualifiedName().text).right()
            else -> Either.left(CompilationError(comparisonExpressionContext.start,
               "invalid numeric entity"))
         }
      }

      if (retVal.size != 2) {
         return Either.left(CompilationError(comparisonExpressionContext.start,
            "invalid numeric entity"))
      } else {
         val mapped = retVal.map {
            when (it) {
               is Either.Right -> {
                 it.b
               }
               is Either.Left -> {
                  return Either.left(CompilationError(comparisonExpressionContext.start,
                     "invalid numeric entity"))
               }
            }
         }
         return ComparisonExpression(ComparisonOperator.forSymbol(comparisonExpressionContextWithOperator.comp_operator().text), mapped[0], mapped[1]).right()
      }
   }

   private fun compileSelectorExpression(selectorBlock: TaxiParser.ConditionalTypeWhenSelectorContext?, namespace: Namespace, targetType: Type): Either<List<CompilationError>, WhenSelectorExpression> {
      return when {
         selectorBlock?.mappedExpressionSelector() != null -> compileTypedAccessor(selectorBlock.mappedExpressionSelector(), namespace, targetType).wrapErrorsInList()
         selectorBlock?.fieldReferenceSelector() != null -> compileFieldReferenceSelector(selectorBlock.fieldReferenceSelector(), targetType)
         else -> EmptyReferenceSelector().right()
      }

   }

   private fun compileFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext, targetType: Type): Either<List<CompilationError>, WhenSelectorExpression> {
      val fieldName = fieldReferenceSelector.Identifier().text
      return compiler.provideField(fieldName, fieldReferenceSelector)
         .flatMap { referencedField ->
            TypeChecking.ifAssignable(referencedField.type, targetType, fieldReferenceSelector) {
               FieldReferenceSelector(fieldName, targetType)
            }.wrapErrorsInList()
         }
   }

   private fun compileTypedAccessor(expressionSelector: TaxiParser.MappedExpressionSelectorContext, namespace: Namespace, targetType: Type): Either<CompilationError, AccessorExpressionSelector> {
      return compiler.parseType(namespace, expressionSelector.typeType()).map { type ->
         val accessor = compiler.compileScalarAccessor(expressionSelector.scalarAccessorExpression(),type)
         AccessorExpressionSelector(accessor, type)
      }
   }
}

