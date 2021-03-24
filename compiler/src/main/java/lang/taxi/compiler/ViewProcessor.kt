package lang.taxi.compiler

import arrow.core.Either
import arrow.core.extensions.either.applicativeError.raiseError
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.toCompilationUnit
import lang.taxi.types.AssignmentExpression
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.FieldAssignmentExpression
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.JoinInfo
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.ValueAssignment
import lang.taxi.types.View
import lang.taxi.types.ViewBodyDefinition
import lang.taxi.types.ViewBodyFieldDefinition
import lang.taxi.types.ViewBodyTypeDefinition
import lang.taxi.types.ViewDefinition
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenCaseMatchExpression
import lang.taxi.types.WhenFieldSetCondition
import lang.taxi.types.WhenSelectorExpression
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList

class ViewProcessor(private val tokenProcessor: TokenProcessor) {
   fun compileView(
      viewName: String,
      namespace: Namespace,
      viewCtx: TaxiParser.ViewDeclarationContext): Either<List<CompilationError>, View> {
      val viewDocumentation = tokenProcessor.parseTypeDoc(viewCtx.typeDoc())
      val annotations = tokenProcessor.collateAnnotations(viewCtx.annotation())
      val modifiers = tokenProcessor.parseModifiers(viewCtx.typeModifier())
      val inherits = tokenProcessor.parseTypeInheritance(namespace, viewCtx.listOfInheritedTypes())
      val viewBodies = compileViewBody(namespace, viewCtx.findBody())
      return viewBodies.map { viewBodies ->
         val viewDefinition = ViewDefinition(
            inheritsFrom = inherits,
            annotations = annotations.toSet(),
            modifiers = modifiers,
            typeDoc = viewDocumentation,
            viewBodyDefinitions = viewBodies,
            compilationUnit = viewCtx.toCompilationUnit())
         View(viewName, viewDefinition)
      }
   }

   private fun compileViewBody(namespace: Namespace, bodyContexts: List<TaxiParser.FindBodyContext>):
      Either<List<CompilationError>, List<ViewBodyDefinition>> {
      val compilationErrors = mutableListOf<CompilationError>()
      val viewBodyDefinitions = mutableListOf<ViewBodyDefinition>()
      bodyContexts.forEach { bodyCtx ->
         resolveViewBody(namespace, bodyCtx)
            .map { viewBodyDefinitions.add(it) }
            .mapLeft { errors -> compilationErrors.addAll(errors) }
      }

      return if (compilationErrors.isNotEmpty()) {
         compilationErrors.left()
      } else {
         viewBodyDefinitions.right()
      }
   }

   private fun validateMultipleBodyDefinitions(bodyDefinitions: List<ViewBodyDefinition>) {
      if (bodyDefinitions.size == 1) {
         bodyDefinitions.right()
      }

   }

   private fun resolveViewBody(namespace: Namespace, bodyCtx: TaxiParser.FindBodyContext):
      Either<List<CompilationError>, ViewBodyDefinition> {
      val joinTypes = bodyCtx.findBodyQuery().joinTo().typeType()
      return listTypeTypeOrError(joinTypes.first()).flatMap { bodyTypeType ->
         tokenProcessor.typeOrError(namespace, bodyTypeType).flatMap { bodyType ->
            if (joinTypes.size > 1) {
               val joinTypeTypeCtx = joinTypes[1]
               listTypeTypeOrError(joinTypeTypeCtx).flatMap { joinType ->
                  tokenProcessor.typeOrError(namespace, joinTypeTypeCtx).flatMap { joinType ->
                     validateJoinBasedViewBodyDefinition(bodyCtx, ViewBodyDefinition(bodyType, joinType))
                        .flatMap { validViewDefinition ->
                           if (bodyCtx.typeBody() != null) {
                              parseViewBodyDefinition(namespace, bodyCtx).flatMap { viewBodyTypeDefinition ->
                                 validViewDefinition.copy(viewBodyTypeDefinition = viewBodyTypeDefinition).right()
                              }.mapLeft { it }
                           } else {
                              validViewDefinition.right()
                           }
                        }
                        .mapLeft { it }
                  }.mapLeft { it }
               }.mapLeft { it }
            } else {
               if (bodyCtx.typeBody() != null) {
                  parseViewBodyDefinition(namespace, bodyCtx).flatMap { viewBodyTypeDefinition ->
                     ViewBodyDefinition(bodyType).copy(viewBodyTypeDefinition = viewBodyTypeDefinition).right()
                  }.mapLeft { it }
               } else {
                  ViewBodyDefinition(bodyType).right()
               }
            }
         }
      }.mapLeft { it }
   }

   private fun parseViewBodyDefinition(namespace: Namespace, bodyCtx: TaxiParser.FindBodyContext): Either<List<CompilationError>, ViewBodyTypeDefinition> {
      val typeBody = bodyCtx.typeBody()
      if (!typeBody.calculatedMemberDeclaration().isNullOrEmpty()) {
         return listOf(CompilationError(typeBody.start, "Calculated member declaration, ${typeBody.calculatedMemberDeclaration().first().text}, is not allowed!")).left()
      }
      val definitionErrors = typeBody.typeMemberDeclaration().map { typeMemberDeclarationContext ->
         val fieldDeclarationContext = typeMemberDeclarationContext.fieldDeclaration()
         val fieldName = fieldDeclarationContext.Identifier().text
         val simplifiedDeclaration = fieldDeclarationContext.simpleFieldDeclaration()
            ?: return listOf(CompilationError(typeBody.start, "Invalid find type body field declaration expected TypeName.TypeName")).left()

         val classOrInterfaceTypeCtx = simplifiedDeclaration.typeType().classOrInterfaceType()
            ?: return listOf(CompilationError(typeBody.start, "Invalid find type body field declaration expected TypeName.TypeName")).left()

         val accessorCtx = simplifiedDeclaration.accessor()
         val sourceTypeName = classOrInterfaceTypeCtx.Identifier().first().text
         tokenProcessor.resolveUserType(namespace, sourceTypeName, bodyCtx, SymbolKind.TYPE_OR_MODEL)
            .flatMap { sourceType ->
               val sourceFieldTypeName = classOrInterfaceTypeCtx.Identifier().last().text
               tokenProcessor.resolveUserType(namespace, sourceFieldTypeName, bodyCtx, SymbolKind.TYPE_OR_MODEL).flatMap { fieldSourceType ->
                  validateViewBodyFieldType(sourceType, fieldSourceType, bodyCtx).flatMap { fieldType ->
                     validateViewFindFieldAccessor(accessorCtx).flatMap { accessor ->
                        ViewBodyFieldDefinition(sourceType = sourceType, fieldType = fieldType, fieldName = fieldName, accessor = accessor).right()
                     }.mapLeft { it }
                  }.mapLeft { it }
               }.mapLeft { it }
            }.mapLeft { it }
      }


      val definitions = mutableListOf<ViewBodyFieldDefinition>()
      definitionErrors.forEach { definitionOrError ->
         when (definitionOrError) {
            is Either.Left -> return definitionOrError
            is Either.Right -> definitions.add(definitionOrError.b)
         }
      }
      return ViewBodyTypeDefinition(definitions.toList()).right()
   }

   private fun validateViewFindFieldAccessor(accessorCtx: TaxiParser.AccessorContext?): Either<List<CompilationError>, ConditionalAccessor?> {
      if (accessorCtx == null) {
         return Either.right(null)
      }

      if (accessorCtx.objectAccessor() != null) {
         return listOf(CompilationError(accessorCtx.start, "only 'when' based definitions are allowed")).left()
      }

      val scalarAccessorCtx = accessorCtx.scalarAccessor()
      val scalarAccessorExpressionCtx = scalarAccessorCtx.scalarAccessorExpression()
      val conditonalTypeCtx = scalarAccessorExpressionCtx.conditionalTypeConditionDeclaration()
         ?: return listOf(CompilationError(accessorCtx.start, "only 'when' based definitions are allowed")).left()
      val fieldExpression = conditonalTypeCtx.fieldExpression()
      if (fieldExpression != null) {
         return listOf(CompilationError(accessorCtx.start, "only 'when' based definitions are allowed")).left()
      }

      if (conditonalTypeCtx.conditionalTypeWhenDeclaration().conditionalTypeWhenSelector() != null) {
         return listOf(CompilationError(accessorCtx.start, "when(this..) is not allowed use when {")).left()
      }

      val caseDeclarations = conditonalTypeCtx.conditionalTypeWhenDeclaration().conditionalTypeWhenCaseDeclaration()

      return processCaseDeclarations(caseDeclarations).flatMap { whenCaseBlocks ->
         val whenFieldSetCondition = WhenFieldSetCondition(EmptyReferenceSelector, whenCaseBlocks)
         ConditionalAccessor(whenFieldSetCondition).right()
      }.mapLeft { it }
   }

   private fun processCaseDeclarations(caseDeclarations: List<TaxiParser.ConditionalTypeWhenCaseDeclarationContext>): Either<List<CompilationError>, List<WhenCaseBlock>> {
      val logicalExpressionCompiler = LogicalExpressionCompiler(this.tokenProcessor)
      val caseBlocks = mutableListOf<WhenCaseBlock>()
      caseDeclarations.forEach { caseDeclaration ->
         val lhs = caseDeclaration.caseDeclarationMatchExpression()
         if (lhs.condition() == null && lhs.caseElseMatchExpression() == null) {
            return listOf(CompilationError(caseDeclaration.start, "invalid case")).left()
         }

         val whenCaseMatchExpressionOrError: Either<CompilationError, WhenCaseMatchExpression> = when {
            lhs.condition() != null -> logicalExpressionCompiler.processLogicalExpressionContext(lhs.condition().logical_expr(), true)
            lhs.caseElseMatchExpression() != null -> ElseMatchExpression.right()
            else -> return listOf(CompilationError(caseDeclaration.start, "invalid case")).left()
         }
         val whenCaseBlockOrError: Either<List<CompilationError>, WhenCaseBlock> = when (whenCaseMatchExpressionOrError) {
            is Either.Right -> {
               compileToValueAssignment(caseDeclaration, logicalExpressionCompiler).flatMap { valueAssignment ->
                  WhenCaseBlock(whenCaseMatchExpressionOrError.b, listOf(InlineAssignmentExpression(valueAssignment))).right()
               }.mapLeft { it }
            }

            is Either.Left -> listOf(whenCaseMatchExpressionOrError.a).left()
         }


         when (whenCaseBlockOrError) {
            is Either.Left -> return whenCaseBlockOrError.a.left()
            is Either.Right -> caseBlocks.add(whenCaseBlockOrError.b)
         }
      }

      return caseBlocks.right()

   }

   private fun compileToValueAssignment(caseDeclaration: TaxiParser.ConditionalTypeWhenCaseDeclarationContext,
                                        logicalExpressionCompiler: LogicalExpressionCompiler): Either<List<CompilationError>, ValueAssignment> {
      return when {
         caseDeclaration.caseScalarAssigningDeclaration() != null ->
            when {
               caseDeclaration.caseScalarAssigningDeclaration().caseFieldReferenceAssignment() != null -> {
                  val identifiers = caseDeclaration.caseScalarAssigningDeclaration().caseFieldReferenceAssignment().Identifier()
                  logicalExpressionCompiler.toViewFindFieldReferenceAssignment(identifiers, caseDeclaration.caseScalarAssigningDeclaration().caseFieldReferenceAssignment())

               }
               caseDeclaration.caseScalarAssigningDeclaration().literal() != null -> {
                  logicalExpressionCompiler.compileLiteralValueAssignment(caseDeclaration.caseScalarAssigningDeclaration().literal())

               }
               else -> listOf(CompilationError(caseDeclaration.start, "invalid case")).left()

            }

         else -> listOf(CompilationError(caseDeclaration.start, "invalid right hand side definition for the case")).left()
      }
   }

   private fun validateViewBodyFieldType(sourceType: Type, fieldType: Type, bodyCtx: TaxiParser.FindBodyContext): Either<List<CompilationError>, Type> {
      if (sourceType == fieldType) {
         return fieldType.right()
      }

      if (sourceType !is ObjectType) {
         return listOf(CompilationError(bodyCtx.start, "${sourceType.qualifiedName} should be an object type")).left()
      }

      val matches = sourceType.fields.filter { field ->
         if (!field.type.format.isNullOrEmpty()) {
            fieldType.isAssignableTo(field.type)
         } else {
            field.type == fieldType
         }

      }

      return when {
         matches.size != 1 -> listOf(CompilationError(bodyCtx.start, "${sourceType.qualifiedName} does not have 1 field with type ${fieldType.qualifiedName}")).left()
         else -> matches.first().type.right()
      }
   }

   private fun listTypeTypeOrError(typeTypeCtx: TaxiParser.TypeTypeContext): Either<List<CompilationError>, TaxiParser.TypeTypeContext> {
      return if (typeTypeCtx.listType() == null) {
         listOf(CompilationError(typeTypeCtx.start, "Currently, only list types are supported in view definitions. Replace ${typeTypeCtx.text} with ${typeTypeCtx.text}[]")).left()
      } else {
         typeTypeCtx.right()
      }
   }

   private fun validateJoinBasedViewBodyDefinition(bodyCtx: TaxiParser.FindBodyContext, viewBodyDefinition: ViewBodyDefinition):
      Either<List<CompilationError>, ViewBodyDefinition> {
      val bodyType = viewBodyDefinition.bodyType
      val joinType = viewBodyDefinition.joinType!!

      if (bodyType !is ObjectType) {
         return listOf(CompilationError(bodyCtx.start, "${bodyType.qualifiedName} is not an Object Type")).left()
      }

      if (joinType !is ObjectType) {
         return listOf(CompilationError(bodyCtx.start, "${joinType.qualifiedName} is not an Object Type")).left()
      }

      val bodyHasAnIdField = bodyType.fields.filter { it.annotations.firstOrNull { annotation -> annotation.qualifiedName == View.JoinAnnotationName } != null }
      val joinHasAnIdField = joinType.fields.filter { it.annotations.firstOrNull { annotation -> annotation.qualifiedName == View.JoinAnnotationName } != null }

      if (bodyHasAnIdField.size == 1 &&
         joinHasAnIdField.size == 1 &&
         bodyHasAnIdField[0].type.basePrimitive != null &&
         joinHasAnIdField[0].type.basePrimitive != null &&
         bodyHasAnIdField[0].type.basePrimitive!!.isAssignableTo(joinHasAnIdField[0].type.basePrimitive!!)) {

         return viewBodyDefinition.copy(joinInfo = JoinInfo(bodyHasAnIdField[0], joinHasAnIdField[0])).right()
      }

      val bodyFieldsQualifiedNames = bodyType.fields.map { it.type.qualifiedName }
      val joinFieldsQualifiedNames = joinType.fields.map { it.type.qualifiedName }

      val joinFieldCounts = bodyFieldsQualifiedNames
         .count { bodyFieldTypeQualifiedName -> joinFieldsQualifiedNames.contains(bodyFieldTypeQualifiedName) }

      return when (joinFieldCounts) {
         1 -> viewBodyDefinition.right()
         else -> listOf(CompilationError(bodyCtx.start, "${bodyType.qualifiedName} and ${joinType.qualifiedName} can't be joined")).left()
      }

   }

}
