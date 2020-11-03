package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.*
import lang.taxi.types.*
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.RuleContext

class ConditionalFieldSetProcessor internal constructor(private val compiler: FieldCompiler) {
   fun compileConditionalFieldStructure(fieldBlock: TaxiParser.ConditionalTypeStructureDeclarationContext, namespace: Namespace): Either<List<CompilationError>, ConditionalFieldSet> {

      // TODO  Not sure what to pass for the type here.
      // If we're here, we're compiling a destructed type block.  eg:
//      (   dealtAmount : String
//        settlementAmount : String
//    ) by when( xpath("/foo/bar") : String) { ...
      // We'll use Any for now, but suspect this needs revisiting
      return compileCondition(fieldBlock.conditionalTypeConditionDeclaration(), namespace, PrimitiveType.ANY).flatMap { condition ->
         fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
            val fieldName = fieldDeclaration.fieldDeclaration().Identifier().text
            compiler.provideField(fieldName, fieldDeclaration)
               .map { field -> field.copy(readExpression = condition) }

         }.invertEitherList().flattenErrors().map { fields ->
            ConditionalFieldSet(fields, condition)
         }


      }
   }

   fun compileCondition(conditionDeclaration: TaxiParser.ConditionalTypeConditionDeclarationContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, FieldSetExpression> {
      return when {
         conditionDeclaration.conditionalTypeWhenDeclaration() != null -> compileWhenCondition(conditionDeclaration.conditionalTypeWhenDeclaration(), namespace, targetType)
         conditionDeclaration.fieldExpression() != null -> compileFieldExpression(conditionDeclaration.fieldExpression(), namespace, targetType)
         else -> error("Unhandled condition type")
      }
   }

   private fun compileFieldExpression(fieldExpression: TaxiParser.FieldExpressionContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, FieldSetExpression> {
      val field1Name = fieldExpression.propertyToParameterConstraintLhs(0).qualifiedName().text
      val field2Name = fieldExpression.propertyToParameterConstraintLhs(1).qualifiedName().text
      return compiler.provideField(field1Name, fieldExpression.propertyToParameterConstraintLhs(0).qualifiedName())
         .map { FieldReferenceSelector.fromField(it) }
         .flatMap { field1Selector ->
            compiler.provideField(field2Name, fieldExpression.propertyToParameterConstraintLhs(1).qualifiedName())
               .map { field1Selector to FieldReferenceSelector.fromField(it) }
         }.map { (field1Selector: FieldReferenceSelector, field2Selector: FieldReferenceSelector) ->
            val operator = FormulaOperator.forSymbol(fieldExpression.arithmaticOperator().text)
            CalculatedFieldSetExpression(field1Selector, field2Selector, operator)
         }
   }

   private fun getTypeDeclarationContext(parserRuleContext: RuleContext?): TaxiParser.TypeDeclarationContext? {
      return when {
         parserRuleContext is TaxiParser.TypeDeclarationContext -> parserRuleContext
         parserRuleContext?.parent != null -> getTypeDeclarationContext(parserRuleContext.parent)
         else -> null
      }
   }

   private fun compileWhenCondition(whenBlock: TaxiParser.ConditionalTypeWhenDeclarationContext, namespace: Namespace, whenSelectorType: Type): Either<List<CompilationError>, WhenFieldSetCondition> {
      return compileSelectorExpression(whenBlock.conditionalTypeWhenSelector(), namespace, whenSelectorType).flatMap { selectorExpression ->
         val cases = compileWhenCases(whenBlock.conditionalTypeWhenCaseDeclaration(), selectorExpression.declaredType, whenSelectorType)
            .invertEitherList().flattenErrors()
            .map { cases ->
               WhenFieldSetCondition(selectorExpression, cases)
            }
         cases
      }
   }

   private fun compileWhenCases(conditionalTypeWhenCaseDeclaration: List<TaxiParser.ConditionalTypeWhenCaseDeclarationContext>, whenSelectorType: Type, assignmentTargetType: Type): List<Either<List<CompilationError>, WhenCaseBlock>> {
      return conditionalTypeWhenCaseDeclaration.map { compileWhenCase(it, whenSelectorType, assignmentTargetType) }
   }

   private fun compileWhenCase(whenCase: TaxiParser.ConditionalTypeWhenCaseDeclarationContext, whenClauseSelectorType: Type, assignmentTargetType: Type): Either<List<CompilationError>, WhenCaseBlock> {
      return compileMatchExpression(whenCase.caseDeclarationMatchExpression())
         .flatMap { matchExpression ->
            TypeChecking.ifAssignable(matchExpression.type, whenClauseSelectorType, whenCase) { matchExpression }.wrapErrorsInList()
         }
         .flatMap { matchExpression ->
            val assignments: Either<List<CompilationError>, List<AssignmentExpression>> = when {
               whenCase.caseFieldAssignmentBlock() != null -> {
                  whenCase.caseFieldAssignmentBlock().caseFieldAssigningDeclaration().map {
                     compileFieldAssignment(it)
                  }.invertEitherList().flattenErrors()
               }
               whenCase.caseScalarAssigningDeclaration() != null -> {
                  compileScalarFieldAssignment(whenCase.caseScalarAssigningDeclaration(), assignmentTargetType)
                     .flatMap { assignmentExpression ->
                        TypeChecking.ifAssignable(assignmentExpression.assignment.type, assignmentTargetType, whenCase.caseScalarAssigningDeclaration()) {
                           listOf(assignmentExpression)
                        }.wrapErrorsInList()
                     }


               }
               else -> error("Unhandled when case branch")
            }
            assignments.map { assignmentExpressions ->
               WhenCaseBlock(matchExpression, assignmentExpressions)
            }
         }
   }

   private fun compileScalarFieldAssignment(scalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext, whenClauseSelectorType: Type): Either<List<CompilationError>, InlineAssignmentExpression> {
      return when {
         scalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(scalarAssigningDeclaration.literal())
         scalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(scalarAssigningDeclaration.caseFieldReferenceAssignment())
         scalarAssigningDeclaration.scalarAccessorExpression() != null -> {
            val accessor = compiler.compileScalarAccessor(scalarAssigningDeclaration.scalarAccessorExpression(), targetType = whenClauseSelectorType)
            ScalarAccessorValueAssignment(accessor).right()
         }
         else -> error("Unhandled scalar value assignment")
      }.map { assignment ->
         InlineAssignmentExpression(assignment)
      }
   }

   private fun compileFieldAssignment(caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext): Either<List<CompilationError>, FieldAssignmentExpression> {
      val fieldName = caseFieldAssignment.Identifier().text
      return compiler.provideField(fieldName, caseFieldAssignment).flatMap { field ->
         compileFieldAssignment(caseFieldAssignment, field.type)
      }
   }

   private fun compileFieldAssignment(caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext, type: Type): Either<List<CompilationError>, FieldAssignmentExpression> {
      return when {
         caseFieldAssignment.caseFieldDestructuredAssignment() != null -> {
            if (type is ObjectType) {
               compileDestructuredValueAssignment(caseFieldAssignment.caseFieldDestructuredAssignment(), type)
            } else {
               listOf(CompilationError(caseFieldAssignment.caseFieldDestructuredAssignment().start, "${type.qualifiedName} can not be declared like this, as it doesn't have any fields")).left()
            }

         }
         caseFieldAssignment.caseScalarAssigningDeclaration() != null -> compileCaseScalarAssignment(caseFieldAssignment.caseScalarAssigningDeclaration())
         caseFieldAssignment.scalarAccessor() != null -> {
            compileScalarAccessorValueAssignment(caseFieldAssignment.scalarAccessor(), type).right()
         }
         else -> error("Unhandled object field value assignment")
      }
         .map { assignment ->
            FieldAssignmentExpression(caseFieldAssignment.Identifier().text, assignment)
         }
   }

   private fun compileCaseScalarAssignment(caseScalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext): Either<List<CompilationError>, ValueAssignment> {
      return when {
         caseScalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(caseScalarAssigningDeclaration.caseFieldReferenceAssignment())
         caseScalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(caseScalarAssigningDeclaration.literal())
         else -> error("Unhandled case scalar assignment")
      }


   }

   private fun compileScalarAccessorValueAssignment(scalarAccessor: TaxiParser.ScalarAccessorContext, targetType: Type): ScalarAccessorValueAssignment {
      val accessor = compiler.compileScalarAccessor(scalarAccessor, targetType) // TODO : Where do we find the type info for this?
      return ScalarAccessorValueAssignment(accessor)
   }

   private fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, ValueAssignment> {
      return if (literal.valueOrNull() == null) {
         NullAssignment.right()
      } else {
         LiteralAssignment(literal.value()).right()
      }

   }

   private fun compileReferenceValueAssignment(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): Either<List<CompilationError>, ValueAssignment> {
      return if (caseFieldReferenceAssignment.Identifier().size > 1) {
         // This is a Foo.Bar -- lets check to see if we can resolve this as an enum
         compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment)
      } else {
         val fieldName = caseFieldReferenceAssignment.text
         this.compiler.provideField(fieldName, caseFieldReferenceAssignment)
            .map { field -> ReferenceAssignment.fromField(field) }
      }
   }

   private fun compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): Either<List<CompilationError>, ValueAssignment> {
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
      }.wrapErrorsInList()
      return enumReference
   }

   private fun compileDestructuredValueAssignment(caseFieldDestructuredAssignment: TaxiParser.CaseFieldDestructuredAssignmentContext, destructuredFieldType: ObjectType): Either<List<CompilationError>, ValueAssignment> {
      return caseFieldDestructuredAssignment.caseFieldAssigningDeclaration().map { caseFieldAssigningContext ->
         val fieldName = caseFieldAssigningContext.Identifier().text
         if (destructuredFieldType.hasField(fieldName)) {
            val field = destructuredFieldType.field(fieldName)
            compileFieldAssignment(caseFieldAssigningContext, field.type)
         } else {
           listOf(CompilationError(caseFieldAssigningContext.start,"Type ${destructuredFieldType.qualifiedName} does not declare a field $fieldName")).left()
         }

      }
         .invertEitherList().flattenErrors()
         .map { fieldAssignmentExpressions: List<FieldAssignmentExpression> ->
            DestructuredAssignment(fieldAssignmentExpressions)
         }
   }

   private fun compileMatchExpression(caseDeclarationMatchExpression: TaxiParser.CaseDeclarationMatchExpressionContext): Either<List<CompilationError>, WhenCaseMatchExpression> {
      return when {
         caseDeclarationMatchExpression.Identifier() != null -> {
            val fieldName = caseDeclarationMatchExpression.Identifier().text
            compiler.provideField(fieldName, caseDeclarationMatchExpression).map { field ->
               ReferenceCaseMatchExpression.fromField(field)
            }
         }
         caseDeclarationMatchExpression.literal() != null -> LiteralCaseMatchExpression(caseDeclarationMatchExpression.literal().value()).right()
         caseDeclarationMatchExpression.caseElseMatchExpression() != null -> ElseMatchExpression.right()
         caseDeclarationMatchExpression.enumSynonymSingleDeclaration() != null -> {
            val enumValueQualifiedName = caseDeclarationMatchExpression.enumSynonymSingleDeclaration().qualifiedName().Identifier().text()
            val (enumTypeName, enumValue) = EnumValue.qualifiedNameFrom(enumValueQualifiedName)
            compiler.typeResolver(caseDeclarationMatchExpression.findNamespace())
               .resolve(enumTypeName.fullyQualifiedName, caseDeclarationMatchExpression)
               .wrapErrorsInList()
               .flatMap { type ->
                  if (type !is EnumType) {
                     listOf(CompilationError(caseDeclarationMatchExpression.start, "Type ${type.qualifiedName} is not an enum")).left()
                  } else {
                     if (type.has(enumValue)) {
                        EnumLiteralCaseMatchExpression(type.of(enumValue), type).right()
                     } else {
                        listOf(CompilationError(caseDeclarationMatchExpression.start, "'$enumValue' is not defined on enum ${type.qualifiedName}")).left()
                     }
                  }
               }

         }

         else -> error("Unhandled case match expression")
      }
   }

   private fun compileSelectorExpression(selectorBlock: TaxiParser.ConditionalTypeWhenSelectorContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, WhenSelectorExpression> {
      return when {
         selectorBlock.mappedExpressionSelector() != null -> compileTypedAccessor(selectorBlock.mappedExpressionSelector(), namespace, targetType)
         selectorBlock.fieldReferenceSelector() != null -> compileFieldReferenceSelector(selectorBlock.fieldReferenceSelector(), targetType)
         else -> error("Unhandled where block selector condition")
      }

   }

   private fun compileFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext, targetType: Type): Either<List<CompilationError>, WhenSelectorExpression> {
      val fieldName = fieldReferenceSelector.Identifier().text
      val field = compiler.provideField(fieldName, fieldReferenceSelector).map { field ->
         // This is the selector in a when condition.
         // eg:
//          identifierValue : Identifier by ---> when (this.assetClass) <--- That bit {
//             ...
//            }
         // We don't do type checking here, as we're setting up a when clause.
         // it's not being assigned to the field -- the result of the when clause is.
         FieldReferenceSelector(fieldName, field.type)
      }
      return field
   }

   private fun compileTypedAccessor(expressionSelector: TaxiParser.MappedExpressionSelectorContext, namespace: Namespace, targetType: Type): Either<List<CompilationError>, AccessorExpressionSelector> {
      return compiler.parseType(namespace, expressionSelector.typeType()).map { type ->
         val accessor = compiler.compileScalarAccessor(expressionSelector.scalarAccessorExpression(), type)
         AccessorExpressionSelector(accessor, type)
      }.wrapErrorsInList()
   }
}

