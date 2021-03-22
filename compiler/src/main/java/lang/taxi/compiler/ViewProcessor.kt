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
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.View
import lang.taxi.types.ViewBodyDefinition
import lang.taxi.types.ViewBodyFieldDefinition
import lang.taxi.types.ViewBodyTypeDefinition
import lang.taxi.types.ViewDefinition

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
         return listOf(CompilationError(accessorCtx.start, "when(this.xxx) not allowed, please use when { case1 -> value1...}")).left()
      }
      val whenExpression = conditonalTypeCtx.conditionalTypeWhenDeclaration()

      return Either.right(null)
   }

   private fun validateViewBodyFieldType(sourceType: Type, fieldType: Type, bodyCtx: TaxiParser.FindBodyContext): Either<List<CompilationError>, Type> {
      if (sourceType !is ObjectType) {
         return listOf(CompilationError(bodyCtx.start, "${sourceType.qualifiedName} should be an object type")).left()
      }
      if (sourceType == fieldType) {
         return fieldType.right()
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
         return viewBodyDefinition.right()
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
