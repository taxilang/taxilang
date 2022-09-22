package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.CompilationMessage
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.accessors.NullValue
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.toCompilationUnits
import lang.taxi.types.AssignmentExpression
import lang.taxi.types.ConditionalFieldSet
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.FieldAssignmentExpression
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FieldSetExpression
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenFieldSetCondition
import lang.taxi.types.WhenSelectorExpression
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.ParserRuleContext

class ConditionalFieldSetProcessor internal constructor(
   private val compiler: FieldCompiler,
   private val expressionCompiler: ExpressionCompiler
) {
   private val typeChecker = compiler.typeChecker
   fun compileConditionalFieldStructure(
      fieldBlock: TaxiParser.ConditionalTypeStructureDeclarationContext,
      namespace: Namespace
   ): Either<List<CompilationError>, ConditionalFieldSet> {

      // TODO  Not sure what to pass for the type here.
      // If we're here, we're compiling a destructed type block.  eg:
//      (   dealtAmount : String
//        settlementAmount : String
//    ) by when( xpath("/foo/bar") : String) { ...
      // We'll use Any for now, but suspect this needs revisiting
      return compileCondition(
         fieldBlock.conditionalTypeConditionDeclaration(),
         namespace,
         PrimitiveType.ANY
      ).flatMap { condition ->
         fieldBlock.typeMemberDeclaration().mapNotNull { fieldDeclaration ->
            val fieldName = fieldDeclaration.fieldDeclaration().identifier().text
            compiler.provideField(fieldName, fieldDeclaration)
               .map { field -> field.copy(readExpression = condition) }

         }.invertEitherList().flattenErrors().map { fields ->
            ConditionalFieldSet(fields, condition)
         }


      }
   }

   fun compileCondition(
      conditionDeclaration: TaxiParser.ConditionalTypeConditionDeclarationContext,
      namespace: Namespace,
      targetType: Type
   ): Either<List<CompilationError>, FieldSetExpression> {
      return when {
         conditionDeclaration.conditionalTypeWhenDeclaration() != null -> compileWhenCondition(
            conditionDeclaration.conditionalTypeWhenDeclaration(),
            namespace,
            targetType
         )
         else -> error("Unhandled condition type")
      }
   }


   private fun compileWhenCondition(
      whenBlock: TaxiParser.ConditionalTypeWhenDeclarationContext,
      namespace: Namespace,
      whenSelectorType: Type
   ): Either<List<CompilationError>, WhenFieldSetCondition> {
       return when {
         whenBlock.expressionGroup() != null ->  expressionCompiler.compile(whenBlock.expressionGroup())

         // These are cases where there isn't an explicit when (matchClause),
         // but are instead when {
         //    someCondition -> ...
         // }
         // Therefore, we're essentially matching against true
         whenBlock.conditionalTypeWhenCaseDeclaration() != null ->  {
            LiteralExpression(LiteralAccessor(true), whenBlock.toCompilationUnits()).right()
         }
         else -> error("Unhandled when condition: ${whenBlock.text}")
      }.flatMap { selectorExpression ->
         val cases = compileWhenCases(
            whenBlock.conditionalTypeWhenCaseDeclaration(),
            selectorExpression.returnType,
            whenSelectorType
         )
            .invertEitherList().flattenErrors()
            .map { cases ->
               WhenFieldSetCondition(selectorExpression, cases)
            }
         cases
      }
   }

   private fun compileWhenCases(
      conditionalTypeWhenCaseDeclaration: List<TaxiParser.ConditionalTypeWhenCaseDeclarationContext>,
      whenSelectorType: Type,
      assignmentTargetType: Type
   ): List<Either<List<CompilationError>, WhenCaseBlock>> {
      return conditionalTypeWhenCaseDeclaration.map { compileWhenCase(it, whenSelectorType, assignmentTargetType) }
   }

   private fun compileWhenCase(
      whenCase: TaxiParser.ConditionalTypeWhenCaseDeclarationContext,
      whenClauseSelectorType: Type,
      assignmentTargetType: Type
   ): Either<List<CompilationError>, WhenCaseBlock> {
      return compileMatchExpression(whenCase.caseDeclarationMatchExpression())
         .flatMap { matchExpression ->
            typeChecker.ifAssignable(matchExpression.returnType, whenClauseSelectorType, whenCase) { matchExpression }
               .wrapErrorsInList()
         }
         .flatMap { matchExpression ->
            val assignments: Either<List<CompilationError>, List<AssignmentExpression>> = when {
//               whenCase.caseFieldAssignmentBlock() != null -> {
//                  whenCase.caseFieldAssignmentBlock().caseFieldAssigningDeclaration().map {
//                     compileFieldAssignment(it)
//                  }.invertEitherList().flattenErrors()
//               }
               // by xpath(), column(), jsonPath() etc...
               whenCase.expressionGroup() != null -> expressionCompiler.compile(whenCase.expressionGroup())
                  .map { expression ->
                     if (expression is LiteralExpression && expression.literal.value is NullValue) {
                        // Enrich the null with type information
                        LiteralExpression(LiteralAccessor.typedNull(assignmentTargetType), expression.compilationUnits)
                     } else {
                        expression
                     }
                  }
                  .flatMap { expression ->
                     typeCheckExpressionAssignment(
                        expression,
                        assignmentTargetType,
                        whenCase.expressionGroup()
                     )
                  }
                  .map { listOf(InlineAssignmentExpression(it)) }
               whenCase.scalarAccessorExpression() != null -> {
                  compileScalarFieldAssignment(whenCase.scalarAccessorExpression(), assignmentTargetType)
                     .flatMap { expression ->
                        typeChecker.ifAssignable(
                           expression.returnType,
                           assignmentTargetType,
                           whenCase.scalarAccessorExpression()
                        ) {
                           listOf(expression)
                        }.wrapErrorsInList()
                     }
               }


//               }

//               whenCase.modelAttributeTypeReference() != null -> {
//                  compiler.parseModelAttributeTypeReference(whenCase.findNamespace(), whenCase.modelAttributeTypeReference())
//                     .flatMap {(memberSourceType, memberType) ->
//                        listOf(InlineAssignmentExpression(ModelAttributeTypeReferenceAssignment(memberSourceType, memberType))).right()}
//               }
               else -> error("Unhandled when case branch")
            }
            assignments.map { assignmentExpressions ->
               WhenCaseBlock(matchExpression, assignmentExpressions)
            }
         }
   }

   private fun <T : Expression> typeCheckExpressionAssignment(
      expression: T,
      assignmentTargetType: Type,
      context: ParserRuleContext
   ): Either<List<CompilationMessage>, T> {
      return expression.strictReturnType
         .mapLeft { CompilationMessage(context.start, it) }
         .flatMap { expressionReturnType ->
            typeChecker.ifAssignable(expressionReturnType, assignmentTargetType, context) {
               expression
            }
         }
         .wrapErrorsInList()
   }

   private fun compileScalarFieldAssignment(
      scalarAssigningDeclaration: TaxiParser.ScalarAccessorExpressionContext,
      whenClauseSelectorType: Type
   ): Either<List<CompilationError>, AssignmentExpression> {
      return compiler.compileScalarAccessor(scalarAssigningDeclaration, targetType = whenClauseSelectorType)
         .map { accessor -> InlineAssignmentExpression(accessor) }
//      return when {
//
//         scalarAssigningDeclaration.expressionGroup() != null -> expressionCompiler.compile(scalarAssigningDeclaration.expressionGroup())
////         scalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(scalarAssigningDeclaration.literal())
////         scalarAssigningDeclaration.caseFieldReferenceAssignment() != null -> compileReferenceValueAssignment(scalarAssigningDeclaration.caseFieldReferenceAssignment())
////         scalarAssigningDeclaration.scalarAccessorExpression() != null -> {
////            compiler
////               .compileScalarAccessor(scalarAssigningDeclaration.scalarAccessorExpression(), targetType = whenClauseSelectorType)
////               .map { accessor -> ScalarAccessorValueAssignment(accessor) }
////         }
//         else -> error("Unhandled scalar value assignment")
////      }.map { assignment ->
////         InlineAssignmentExpression(assignment)
//      }
   }

   private fun compileFieldAssignment(caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext): Either<List<CompilationError>, FieldAssignmentExpression> {
      val fieldName = caseFieldAssignment.identifier().text
      return compiler.provideField(fieldName, caseFieldAssignment).flatMap { field ->
         compileFieldAssignment(caseFieldAssignment, field.type)
      }
   }

   private fun compileFieldAssignment(
      caseFieldAssignment: TaxiParser.CaseFieldAssigningDeclarationContext,
      type: Type
   ): Either<List<CompilationError>, FieldAssignmentExpression> {
      return when {
//         caseFieldAssignment.caseFieldDestructuredAssignment() != null -> {
//            if (type is ObjectType) {
//               compileDestructuredValueAssignment(caseFieldAssignment.caseFieldDestructuredAssignment(), type)
//            } else {
//               listOf(CompilationError(caseFieldAssignment.caseFieldDestructuredAssignment().start, "${type.qualifiedName} can not be declared like this, as it doesn't have any fields")).left()
//            }
//
//         }
         caseFieldAssignment.caseScalarAssigningDeclaration() != null -> compileCaseScalarAssignment(caseFieldAssignment.caseScalarAssigningDeclaration())
//         caseFieldAssignment.accessor()?.scalarAccessorExpression() != null -> {
//            compileScalarAccessorValueAssignment(caseFieldAssignment.accessor().scalarAccessorExpression(), type)
//         }
         else -> error("Unhandled object field value assignment")
      }
         .map { assignment ->
            FieldAssignmentExpression(caseFieldAssignment.identifier().text, assignment)
         }
   }

   private fun compileCaseScalarAssignment(caseScalarAssigningDeclaration: TaxiParser.CaseScalarAssigningDeclarationContext): Either<List<CompilationError>, Expression> {
      return when {
         caseScalarAssigningDeclaration.expressionGroup() != null -> expressionCompiler.compile(
            caseScalarAssigningDeclaration.expressionGroup()
         )
//         caseScalarAssigningDeclaration.literal() != null -> compileLiteralValueAssignment(caseScalarAssigningDeclaration.literal())
         else -> error("Unhandled case scalar assignment")
      }


   }

//   private fun compileScalarAccessorValueAssignment(scalarAccessor: TaxiParser.ScalarAccessorContext, targetType: Type): Either<List<CompilationError>, ScalarAccessorValueAssignment> {
//      return compiler.compileScalarAccessor(scalarAccessor, targetType).map { accessor ->
//         ScalarAccessorValueAssignment(accessor)
//      }
//   }

//   private fun compileLiteralValueAssignment(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, ValueAssignment> {
//      return if (literal.valueOrNull() == null) {
//         NullAssignment.right()
//      } else {
//         LiteralAssignment(literal.value()).right()
//      }
//
//   }

//   private fun compileReferenceValueAssignment(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): Either<List<CompilationError>, ValueAssignment> {
//      return if (caseFieldReferenceAssignment.identifier().size > 1) {
//         // This is a Foo.Bar -- lets check to see if we can resolve this as an enum
//         compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment)
//      } else {
//         val fieldName = caseFieldReferenceAssignment.text
//         this.compiler.provideField(fieldName, caseFieldReferenceAssignment)
//            .map { field -> ReferenceAssignment.fromField(field) }
//      }
//   }

//   private fun compileReferenceAssignmentAsEnumReference(caseFieldReferenceAssignment: TaxiParser.CaseFieldReferenceAssignmentContext): Either<List<CompilationError>, ValueAssignment> {
//      val enumName = caseFieldReferenceAssignment.identifier().dropLast(1).joinToString(".")
//      val enumReference = compiler.lookupTypeByName(enumName, caseFieldReferenceAssignment).flatMap { typeName ->
//         compiler.typeResolver(caseFieldReferenceAssignment.findNamespace())
//            .resolve(typeName, caseFieldReferenceAssignment)
//            .flatMap { type ->
//               require(type is EnumType) { "Expected $typeName to be an enum" }
//               val enumType = type // for readability
//               val enumReference = caseFieldReferenceAssignment.identifier().last()
//               if (enumType.has(enumReference.text)) {
//                  EnumValueAssignment(enumType, type.of(enumReference.text)).right()
//               } else {
//                  listOf(CompilationError(caseFieldReferenceAssignment.start, "Cannot resolve EnumValue of ${caseFieldReferenceAssignment.identifier().text()}")).left()
//               }
//            }
//      }
//      return enumReference
//   }

//   private fun compileDestructuredValueAssignment(caseFieldDestructuredAssignment: TaxiParser.CaseFieldDestructuredAssignmentContext, destructuredFieldType: ObjectType): Either<List<CompilationError>, ValueAssignment> {
//      return caseFieldDestructuredAssignment.caseFieldAssigningDeclaration().map { caseFieldAssigningContext ->
//         val fieldName = caseFieldAssigningContext.identifier().text
//         if (destructuredFieldType.hasField(fieldName)) {
//            val field = destructuredFieldType.field(fieldName)
//            compileFieldAssignment(caseFieldAssigningContext, field.type)
//         } else {
//            listOf(CompilationError(caseFieldAssigningContext.start, "Type ${destructuredFieldType.qualifiedName} does not declare a field $fieldName")).left()
//         }
//
//      }
//         .invertEitherList().flattenErrors()
//         .map { fieldAssignmentExpressions: List<FieldAssignmentExpression> ->
//            DestructuredAssignment(fieldAssignmentExpressions)
//         }
//   }

   private fun compileMatchExpression(caseDeclarationMatchExpression: TaxiParser.CaseDeclarationMatchExpressionContext): Either<List<CompilationError>, Expression> {
      return when {
         caseDeclarationMatchExpression.expressionGroup() != null -> expressionCompiler.compile(
            caseDeclarationMatchExpression.expressionGroup()
         )
//         caseDeclarationMatchExpression.identifier() != null -> {
//            val fieldName = caseDeclarationMatchExpression.identifier().text
//            compiler.provideField(fieldName, caseDeclarationMatchExpression).map { field ->
//               ReferenceCaseMatchExpression.fromField(field)
//            }
//         }
//         caseDeclarationMatchExpression.literal() != null -> LiteralCaseMatchExpression(caseDeclarationMatchExpression.literal().value()).right()
         caseDeclarationMatchExpression.caseElseMatchExpression() != null -> ElseMatchExpression.right()
//         caseDeclarationMatchExpression.enumSynonymSingleDeclaration() != null -> {
//            val enumValueQualifiedName = caseDeclarationMatchExpression.enumSynonymSingleDeclaration().qualifiedName().identifier().text()
//            val (enumTypeName, enumValue) = EnumValue.splitEnumValueName(enumValueQualifiedName)
//            compiler.typeResolver(caseDeclarationMatchExpression.findNamespace())
//               .resolve(enumTypeName.fullyQualifiedName, caseDeclarationMatchExpression)
//               .flatMap { type ->
//                  if (type !is EnumType) {
//                     listOf(CompilationError(caseDeclarationMatchExpression.start, "Type ${type.qualifiedName} is not an enum")).left()
//                  } else {
//                     if (type.has(enumValue)) {
//                        EnumLiteralCaseMatchExpression(type.of(enumValue), type).right()
//                     } else {
//                        listOf(CompilationError(caseDeclarationMatchExpression.start, "'$enumValue' is not defined on enum ${type.qualifiedName}")).left()
//                     }
//                  }
//               }
//
//         }
//         caseDeclarationMatchExpression.condition() != null -> processLogicalExpressionContext(caseDeclarationMatchExpression.condition().logical_expr()).wrapErrorsInList()

         else -> error("Unhandled case match expression: ${caseDeclarationMatchExpression.text}")
      }
   }
//
//   private fun processLogicalExpressionContext(logicalExpressionCtx: TaxiParser.Logical_exprContext): Either<CompilationError, LogicalExpression> {
//      val logicalExpressionCompiler = LogicalExpressionCompiler(this.compiler.tokenProcessor)
//      return logicalExpressionCompiler.processLogicalExpressionContext(logicalExpressionCtx)
//   }

//   private fun compileSelectorExpression(
//      selectorExpression: TaxiParser.ExpressionGroupContext?,
//      namespace: Namespace,
//      targetType: Type
//   ): Either<List<CompilationError>, Expression> {
//      return when {
//         selectorExpression == null -> error("I think I should return something that evaluates to true here") //EmptyReferenceSelector.right()
//         else -> expressionCompiler.compile(selectorExpression)
////         selectorBlock.mappedExpressionSelector() != null -> compileTypedAccessor(selectorBlock.mappedExpressionSelector(), namespace)
////         selectorBlock.fieldReferenceSelector() != null -> compileFieldReferenceSelector(selectorBlock.fieldReferenceSelector(), targetType)
////         else -> error("Unhandled where block selector condition")
//      }
//
//   }

   private fun compileFieldReferenceSelector(
      fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext,
      targetType: Type
   ): Either<List<CompilationError>, WhenSelectorExpression> {
      val fieldName = fieldReferenceSelector.identifier().text
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
}

