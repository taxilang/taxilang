package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.toCompilationUnit
import lang.taxi.types.ArrayType
import lang.taxi.types.Arrays
import lang.taxi.types.JoinInfo
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.View
import lang.taxi.types.ViewBodyDefinition
import lang.taxi.types.ViewDefinition

class ViewProcessor(private val tokenProcessor: TokenProcessor) {
   private val filterCriteriaProcessor = ViewCriteriaFilterProcessor(tokenProcessor)
   fun compileView(
      viewName: String,
      namespace: Namespace,
      viewCtx: TaxiParser.ViewDeclarationContext): Either<List<CompilationError>, View> {
      val viewDocumentation = tokenProcessor.parseTypeDoc(viewCtx.typeDoc())
      val annotations = tokenProcessor.collateAnnotations(viewCtx.annotation())
      val modifiers = tokenProcessor.parseModifiers(viewCtx.typeModifier())
      val inherits = tokenProcessor.parseTypeInheritance(namespace, viewCtx.listOfInheritedTypes())
      val viewBodies = compileViewBody(namespace, viewCtx.findBody())
      val viewValidator = ViewValidator(viewName)
      val validatedViewBodies = viewBodies.flatMap {
         viewValidator.validateViewBodyDefinitions(it, viewCtx)
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

   private fun resolveViewBody(namespace: Namespace, bodyCtx: TaxiParser.FindBodyContext):
      Either<List<CompilationError>, ViewBodyDefinition> {
      val joinTypes = bodyCtx.findBodyQuery().joinTo().filterableTypeType()
      val firstFilterableType = joinTypes.first()
      return listTypeTypeOrError(firstFilterableType.typeType()).flatMap { bodyTypeType ->
         tokenProcessor.typeOrError(namespace, bodyTypeType).flatMap { bodyType ->
            val bodyMemberType = if (Arrays.isArray(bodyType)) {
               (bodyType as ArrayType).type
            } else {
               bodyType
            }
            if (joinTypes.size > 1) {
               viewBodyDefinitionForJoinFind(joinTypes, namespace, bodyCtx, bodyMemberType)
            } else {
               viewBodyDefinitionForSimpleFind(namespace, bodyCtx, bodyMemberType, firstFilterableType)
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
      bodyType: Type,
      filterableTypeTypeCtx: TaxiParser.FilterableTypeTypeContext): Either<List<CompilationError>, ViewBodyDefinition> {
      return if (bodyCtx.anonymousTypeDefinition() != null) {
         parseViewBodyDefinition(namespace, bodyCtx).flatMap { viewBodyTypeDefinition ->
            if (filterableTypeTypeCtx.filterExpression() != null) {
               filterCriteriaProcessor.processFilterExpressionContext(bodyType, filterableTypeTypeCtx.filterExpression())
                  .flatMap { filterExpression ->
                     ViewBodyDefinition(bodyType = bodyType, bodyTypeFilter = filterExpression, viewBodyType = viewBodyTypeDefinition).right()
                  }
            } else {
               ViewBodyDefinition(bodyType).copy(viewBodyType = viewBodyTypeDefinition).right()
            }
         }.mapLeft { it }
      } else {
         if (filterableTypeTypeCtx.filterExpression() != null) {
            filterCriteriaProcessor.processFilterExpressionContext(bodyType, filterableTypeTypeCtx.filterExpression())
               .flatMap { filterExpression ->
                  ViewBodyDefinition(bodyType = bodyType, bodyTypeFilter = filterExpression).right()
               }
         } else {
            ViewBodyDefinition(bodyType).right()
         }
      }
   }

   /*
    * Case for a find with a join, e.g. find { Foo[] (joinTo Bar[]) } as { .. }
    */
   private fun viewBodyDefinitionForJoinFind(
      joinTypes: List<TaxiParser.FilterableTypeTypeContext>,
      namespace: Namespace,
      bodyCtx: TaxiParser.FindBodyContext,
      bodyType: Type): Either<List<CompilationError>, ViewBodyDefinition> {
      val joinTypeTypeCtx = joinTypes[1]
      return listTypeTypeOrError(joinTypeTypeCtx.typeType()).flatMap { joinType ->
         tokenProcessor.typeOrError(namespace, joinTypeTypeCtx.typeType()).flatMap { joinType ->
            val joinMemberType = Arrays.unwrapPossibleArrayType(joinType)
            validateJoinBasedViewBodyDefinition(bodyCtx, ViewBodyDefinition(bodyType, joinMemberType))
               .flatMap { validViewDefinition ->
                  val firstFilterExpression =  if (joinTypes.first().filterExpression() != null) {
                     filterCriteriaProcessor.processFilterExpressionContext(bodyType, joinTypes.first().filterExpression())
                  } else null

                  val secondFilterExpression = if (joinTypeTypeCtx.filterExpression() != null) {
                     filterCriteriaProcessor.processFilterExpressionContext(joinMemberType, joinTypes[1].filterExpression())
                  } else null

                  if (bodyCtx.anonymousTypeDefinition() != null) {
                     parseViewBodyDefinition(namespace, bodyCtx).flatMap { viewBodyTypeDefinition ->
                        when {
                           firstFilterExpression != null && firstFilterExpression is Either.Left -> firstFilterExpression.value.left()
                           secondFilterExpression != null && secondFilterExpression is Either.Left -> secondFilterExpression.value.left()
                           firstFilterExpression != null && secondFilterExpression != null && firstFilterExpression is Either.Right && secondFilterExpression is Either.Right ->
                              validViewDefinition.copy(viewBodyType = viewBodyTypeDefinition, bodyTypeFilter = firstFilterExpression.value, joinTypeFilter = secondFilterExpression.value)
                                 .right()
                           firstFilterExpression != null && secondFilterExpression == null && firstFilterExpression is Either.Right ->
                              validViewDefinition.copy(viewBodyType = viewBodyTypeDefinition, bodyTypeFilter = firstFilterExpression.value).right()
                           firstFilterExpression == null && secondFilterExpression != null && secondFilterExpression is Either.Right ->
                              validViewDefinition.copy(viewBodyType = viewBodyTypeDefinition, joinTypeFilter = secondFilterExpression.value).right()
                           else ->  validViewDefinition.copy(viewBodyType = viewBodyTypeDefinition).right()
                        }
                     }.mapLeft { it }
                  } else {
                     when {
                        firstFilterExpression != null && firstFilterExpression is Either.Left -> firstFilterExpression.value.left()
                        secondFilterExpression != null && secondFilterExpression is Either.Left -> secondFilterExpression.value.left()
                        firstFilterExpression != null && secondFilterExpression != null && firstFilterExpression is Either.Right && secondFilterExpression is Either.Right ->
                           validViewDefinition.copy(bodyTypeFilter = firstFilterExpression.value, joinTypeFilter = secondFilterExpression.value).right()
                        firstFilterExpression != null && secondFilterExpression == null && firstFilterExpression is Either.Right ->
                           validViewDefinition.copy(bodyTypeFilter = firstFilterExpression.value).right()
                        firstFilterExpression == null && secondFilterExpression != null && secondFilterExpression is Either.Right ->
                           validViewDefinition.copy(joinTypeFilter = secondFilterExpression.value).right()
                        else ->  validViewDefinition.right()
                     }
                  }
               }
               .mapLeft { it }
         }.mapLeft { it }
      }.mapLeft { it }
   }


   private fun parseViewBodyDefinition(namespace: Namespace, bodyCtx: TaxiParser.FindBodyContext): Either<List<CompilationError>, Type> {
      val anonymousTypeDefinitionContext = bodyCtx.anonymousTypeDefinition()
      return this.tokenProcessor.parseAnonymousType(
         namespace,
         anonymousTypeDefinitionContext
      )
   }


   private fun listTypeTypeOrError(typeTypeCtx: TaxiParser.TypeTypeContext): Either<List<CompilationError>, TaxiParser.TypeTypeContext> {

      return if (typeTypeCtx.listType() == null) {
         listOf(CompilationError(typeTypeCtx.start, "Currently, only list types are supported in view definitions. Replace ${typeTypeCtx.text} with ${typeTypeCtx.text}[]")).left()
      } else if(typeTypeCtx.parameterConstraint() != null) {
         listOf(CompilationError(typeTypeCtx.start, "Replace ${typeTypeCtx.parameterConstraint().text} with (${typeTypeCtx.parameterConstraint().text})")).left()
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
}
