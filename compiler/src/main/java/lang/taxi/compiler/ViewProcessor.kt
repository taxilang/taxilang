package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.toCompilationUnit
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.EnumType
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.JoinInfo
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
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
      val validatedViewBodies = viewBodies.flatMap {
         validateViewBodyDefinitions(it, viewCtx)
      }
      return validatedViewBodies.map { viewBodies ->
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
      Either<List<CompilationError>, List<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>> {
      val compilationErrors = mutableListOf<CompilationError>()
      val viewBodyDefinitions = mutableListOf<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>()
      bodyContexts.forEach { bodyCtx ->
         resolveViewBody(namespace, bodyCtx)
            .map { viewBodyDefinitions.add(Pair(it, bodyCtx)) }
            .mapLeft { errors -> compilationErrors.addAll(errors) }
      }

      return if (compilationErrors.isNotEmpty()) {
         compilationErrors.left()
      } else {
         viewBodyDefinitions.right()
      }
   }

   private fun validateViewBodyDefinitions(
      bodyDefinitions: List<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>,
      viewCtx: TaxiParser.ViewDeclarationContext): Either<List<CompilationError>, List<ViewBodyDefinition>> {
      if (bodyDefinitions.size == 1 && bodyDefinitions.first().first.joinType == null) {
         bodyDefinitions.right()
      }

      val compilationErrors = mutableListOf<CompilationError>()
      val viewFieldTypes = mutableListOf<List<PrimitiveType>>()
      bodyDefinitions.forEach { (bodyDefinition, bodyCtx) ->
         val viewBodyTypeDefinition = bodyDefinition.viewBodyTypeDefinition
         if (viewBodyTypeDefinition == null) {
            compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition - find with an empty body (as {} block is missing"))
         } else {
            val fields = viewBodyTypeDefinition.fields
            if (fields.isEmpty()) {
               compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition - empty as {} block"))
            }
            viewFieldTypes.add(fromViewBodyFieldDefinitionToPrimitiveFields(fields, bodyDefinition, bodyCtx,compilationErrors))
         }
      }

      if (viewFieldTypes.size > 1) {
         val firstViewDefinitionPrimitiveFields = viewFieldTypes.removeAt(0)
         if (viewFieldTypes.any { firstViewDefinitionPrimitiveFields != it }) {
            compilationErrors.add(CompilationError(viewCtx.start, "Invalid View Definition - individual find expressions should have compatible 'as' blocks."))
         }
      }

      return if (compilationErrors.isEmpty()) {
         bodyDefinitions.map { it.first }.right()
      } else {
         compilationErrors.left()
      }
   }

   private fun fromViewBodyFieldDefinitionToPrimitiveFields(
      fields: List<ViewBodyFieldDefinition>,
     bodyDefinition: ViewBodyDefinition,
      bodyCtx: TaxiParser.FindBodyContext,
      compilationErrors: MutableList<CompilationError>): List<PrimitiveType> {
      return fields.mapNotNull { viewBodyFieldDefinition ->
         when(val res = validateViewBodyFieldDefinition(viewBodyFieldDefinition, bodyDefinition, bodyCtx)) {
            is Either.Left -> {
               compilationErrors.add(res.a)
               null
            }
            is Either.Right -> res.b
         }
      }

   }

   private fun validateViewBodyFieldDefinition(
      viewBodyFieldDefinition: ViewBodyFieldDefinition,
      bodyDefinition: ViewBodyDefinition,
      findBodyCtx: TaxiParser.FindBodyContext): Either<CompilationError, PrimitiveType> {
      return if ((viewBodyFieldDefinition.sourceType != viewBodyFieldDefinition.fieldType) &&
         (viewBodyFieldDefinition.sourceType != bodyDefinition.bodyType) &&
         (viewBodyFieldDefinition.sourceType != bodyDefinition.joinType)) {
         CompilationError(findBodyCtx.start, "Invalid View Definition - ${viewBodyFieldDefinition.sourceType.toQualifiedName().typeName} is not valid to use!")
            .left()
      } else {
         getPrimitiveTypeForField(viewBodyFieldDefinition.fieldType, viewBodyFieldDefinition.fieldName, findBodyCtx)
      }
   }


   private fun resolveViewBody(namespace: Namespace, bodyCtx: TaxiParser.FindBodyContext):
      Either<List<CompilationError>, ViewBodyDefinition> {
      val joinTypes = bodyCtx.findBodyQuery().joinTo().typeType()
      return listTypeTypeOrError(joinTypes.first()).flatMap { bodyTypeType ->
         tokenProcessor.typeOrError(namespace, bodyTypeType).flatMap { bodyType ->
            if (joinTypes.size > 1) {
               viewBodyDefinitionForJoinFind(joinTypes, namespace, bodyCtx, bodyType)
            } else {
               viewBodyDefinitionForSimpleFind(namespace, bodyCtx, bodyType)
            }
         }
      }.mapLeft { it }
   }

   /*
    * Case for a find without a join, e.g. find { Foo[] } as {...}
    */
   private fun viewBodyDefinitionForSimpleFind(
      namespace: Namespace,
      bodyCtx: TaxiParser.FindBodyContext,
      bodyType: Type): Either<List<CompilationError>, ViewBodyDefinition> {
      return if (bodyCtx.typeBody() != null) {
         parseViewBodyDefinition(namespace, bodyCtx).flatMap { viewBodyTypeDefinition ->
            ViewBodyDefinition(bodyType).copy(viewBodyTypeDefinition = viewBodyTypeDefinition).right()
         }.mapLeft { it }
      } else {
         ViewBodyDefinition(bodyType).right()
      }
   }

   /*
    * Case for a find with a join, e.g. find { Foo[] (joinTo Bar[]) } as { .. }
    */
   private fun viewBodyDefinitionForJoinFind(
      joinTypes: List<TaxiParser.TypeTypeContext>,
      namespace: Namespace,
      bodyCtx: TaxiParser.FindBodyContext,
      bodyType: Type): Either<List<CompilationError>, ViewBodyDefinition> {
      val joinTypeTypeCtx = joinTypes[1]
      return listTypeTypeOrError(joinTypeTypeCtx).flatMap { joinType ->
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

   /*
    * Checks whether a join expression in a find statement is valid.
    * given
    * find { Foo[] (joinTo Bar[]) }
    * Requires that Foo and Bar has a common field that we can join on, This common field is determined as:
    *  - a field with @Id annotation on both Foo and Bar
    *  - a single common field between Foo and Bar
    * This method validates above rules.
    */
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

      // Check whether both types has @Id annotated fields.
      val bodyHasAnIdField = bodyType.fields.filter { it.annotations.firstOrNull { annotation -> annotation.qualifiedName == View.JoinAnnotationName } != null }
      val joinHasAnIdField = joinType.fields.filter { it.annotations.firstOrNull { annotation -> annotation.qualifiedName == View.JoinAnnotationName } != null }

      // Check whether Both have only one @Id annotated field and these fields are compatible.
      if (bodyHasAnIdField.size == 1 &&
         joinHasAnIdField.size == 1 &&
         bodyHasAnIdField[0].type.basePrimitive != null &&
         joinHasAnIdField[0].type.basePrimitive != null &&
         bodyHasAnIdField[0].type.basePrimitive!!.isAssignableTo(joinHasAnIdField[0].type.basePrimitive!!)) {
         return viewBodyDefinition.copy(joinInfo = JoinInfo(bodyHasAnIdField[0], joinHasAnIdField[0])).right()
      }

      // No @Id annotation based match, so look for another common field that we can join on.
      val bodyFieldsQualifiedNames = bodyType.fields.map { it.type.qualifiedName }
      val joinFieldsQualifiedNames = joinType.fields.map { it.type.qualifiedName }

      var commonFieldQualifiedName: String? = null
      val joinFieldCounts = bodyFieldsQualifiedNames
         .count { bodyFieldTypeQualifiedName ->
            val match = joinFieldsQualifiedNames.contains(bodyFieldTypeQualifiedName)
            if (match) {
               commonFieldQualifiedName = bodyFieldTypeQualifiedName
            }
            match

         }

      return when (joinFieldCounts) {
         1 -> {
            val bodyField = bodyType.fields.first { it.type.qualifiedName == commonFieldQualifiedName }
            val joinField = joinType.fields.first { it.type.qualifiedName == commonFieldQualifiedName }
            viewBodyDefinition.copy(joinInfo = JoinInfo(bodyField, joinField)).right()
         }
         else -> listOf(CompilationError(bodyCtx.start, "${bodyType.qualifiedName} and ${joinType.qualifiedName} can't be joined. Ensure that both types in join expression has a single property with Id annotation.")).left()
      }

   }

   //  ensure that field type can be mapped to a database column
   // as currently we restrict view to sql Views. Remove this when it is relaxed.
   private fun getPrimitiveTypeForField(fieldType: Type, fieldName: String, findBodyCtx: TaxiParser.FindBodyContext):
      Either<CompilationError, PrimitiveType> {
      return when {
         PrimitiveType.isAssignableToPrimitiveType(fieldType) -> {
            PrimitiveType.getUnderlyingPrimitive(fieldType).right()
         }
         fieldType is EnumType -> {
            PrimitiveType.STRING.right()
         }
         fieldType.inheritsFrom.size == 1 -> {
            getPrimitiveTypeForField(fieldType.inheritsFrom.first(), fieldName, findBodyCtx)
         }
         else -> CompilationError(findBodyCtx.start, "type ${fieldType.qualifiedName} for field $fieldName is not allowed in view definitions").left()
      }
   }

}