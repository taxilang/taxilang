package lang.taxi.compiler.fields

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.TypeProjectionContext
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.accessors.*
import lang.taxi.compiler.*
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.ParserRuleContext
import java.util.*

class FieldCompiler(
   internal val tokenProcessor: TokenProcessor,
   private val typeBody: TypeWithFieldsContext,
   private val typeName: String,
   private val errors: MutableList<CompilationError>,
   private val resolutionContext: ResolutionContext = ResolutionContext()
) {
   internal val typeChecker = tokenProcessor.typeChecker
//   private val conditionalFieldSetProcessor =
//      ConditionalFieldSetProcessor(this, ExpressionCompiler(tokenProcessor, typeChecker, errors, this))

   private val fieldsBeingCompiled = mutableSetOf<String>()
   private val compiledFields = mutableMapOf<String, Either<List<CompilationError>, Field>>()

   private val fieldNamesToDefinitions: Map<String, TaxiParser.TypeMemberDeclarationContext> by lazy {
      fun getFieldNameAndDeclarationContext(memberDeclaration: TaxiParser.TypeMemberDeclarationContext): Pair<String, TaxiParser.TypeMemberDeclarationContext> {
          return TokenProcessor.unescape(memberDeclaration.fieldDeclaration().identifier().text) to memberDeclaration
      }

      val fields = typeBody.memberDeclarations
         .map { getFieldNameAndDeclarationContext(it) }
         .toList()

      // SPIKE: removing "conditional types"
//      val fieldsInFieldBlocks = typeBody.conditionalTypeDeclarations.flatMap { fieldBlock ->
//         fieldBlock.typeMemberDeclaration().map { memberDeclarationContext ->
//            getFieldNameAndDeclarationContext(memberDeclarationContext)
//         }
//      }.toList()

      // Check for duplicate field declarations
      val allFields = fields //+ fieldsInFieldBlocks
      val duplicateDefinitionErrors = allFields.groupBy({ it.first }) { it.second }
         .filter { (_, declarationSites) -> declarationSites.size > 1 }
         .toList()
         .flatMap { (fieldName, declarationSites) ->
            declarationSites.map { fieldDeclarationSite ->
               CompilationError(fieldDeclarationSite.start, "Field $fieldName is declared multiple times")
            }
         }
      this.errors.addAll(duplicateDefinitionErrors)
      allFields.toMap()
   }

   fun provideField(fieldName: String, requestingToken: ParserRuleContext): Either<List<CompilationError>, Field> {
      if (fieldsBeingCompiled.contains(fieldName)) {
         return listOf(
            CompilationError(
               requestingToken.start,
               "Cyclic dependency detected - field $fieldName is currently being compiled"
            )
         ).left()
      }
      fieldsBeingCompiled.add(fieldName)
      val field = compiledFields.getOrPut(fieldName) {
         compileField(fieldName, requestingToken)
      }
      fieldsBeingCompiled.remove(fieldName)
      return field
   }

   fun compileAllFields(): List<Field> {
      // SPIKE: Removing Conditional Fields
//      val namespace = typeBody.findNamespace()
//      val conditionalFieldStructures = typeBody.conditionalTypeDeclarations.mapNotNull { conditionalFieldBlock ->
//         conditionalFieldSetProcessor.compileConditionalFieldStructure(conditionalFieldBlock, namespace)
//            .collectErrors(errors).getOrElse { null }
//      }

//      val fieldsWithConditions = conditionalFieldStructures.flatMap { it.fields }

      val fields = buildObjectFields(typeBody.memberDeclarations)

      val spreadFields = buildSpreadFieldsIfEnabled()

      return (fields /*+ fieldsWithConditions*/ + spreadFields).distinctBy { it.name }
   }

   private fun buildObjectFields(memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>): List<Field> {
      val fields = memberDeclarations.map { member ->
         provideField(TokenProcessor.unescape(member.fieldDeclaration().identifier().text), member)
      }.mapNotNull { either -> either.collectErrors(errors).getOrElse { null } }
      return fields
   }

   private fun buildSpreadFieldsIfEnabled(): List<Field> {
      if (!typeBody.hasSpreadOperator) {
         return emptyList()
      }

      // Use resolutionContext instead of typeBody for determining
      // the type that we're spreading across.
      // This is because in field projections, typeBody is set for simple cases:
      // find { Person } as {
      //    address : Address as {
      //        ... except {streetName, secretCode}
      //    }
      //
      // but not for inline field expressions:
      // find { Movie[] } as {
      //    cast : Person[]
      //    aListers : filterAll(this.cast, (Person) -> containsString(PersonName, 'a')) as  { // Inferred return type is Person
      //       ...except { id }
      //    }
      //}[]
      val typeBeingSpread = resolutionContext.activeScopes.lastOrNull()?.type?.let { possibleArrayType ->
         // When we're projecting a collection, (in the above example)
         // the projection scope is an Array,
         // but the spread operator should apply to the member
         ArrayType.memberTypeIfArray(possibleArrayType)
      }
      val fields = when (typeBeingSpread) {
         is ObjectType -> typeBeingSpread.fields
         else -> emptyList()
      }

      val excludedFields = typeBody.spreadOperatorExcludedFields
      return fields.filter { !excludedFields.contains(it.name) }
   }

   private fun compileField(
      fieldName: String,
      requestingToken: ParserRuleContext
   ): Either<List<CompilationError>, Field> {
      val memberDeclaration = fieldNamesToDefinitions[fieldName]
         ?: return listOf(
            CompilationError(
               requestingToken.start,
               "Field $fieldName does not exist on type $typeName"
            )
         ).left()

      return compileField(memberDeclaration)
   }

   private fun compileField(member: TaxiParser.TypeMemberDeclarationContext): Either<List<CompilationError>, Field> {
      val fieldAnnotations = tokenProcessor.collateAnnotations(member.annotation())

      val typeDoc = tokenProcessor.parseTypeDoc(member.typeDoc())
      val namespace = member.findNamespace()
      val fieldTypeDeclaration: TaxiParser.FieldTypeDeclarationContext? =
         member.fieldDeclaration().fieldTypeDeclaration()
      val isChildOfProjection = typeBody.objectType?.qualifiedName != null
      // orderId: {  foo: String }
      val anonymousTypeDefinition = member.fieldDeclaration().anonymousTypeDefinition()

      if ((fieldTypeDeclaration == null && anonymousTypeDefinition == null) && isChildOfProjection) {
         return resolveImplicitTypeFromToBeProjectedType(member)
      }
      // orderId: Order::OrderId
      val modelAttributeType = member.fieldDeclaration().modelAttributeTypeReference()
      val expressionGroup: TaxiParser.ExpressionGroupContext? = member.fieldDeclaration().expressionGroup()
      val expressionCompiler = tokenProcessor.expressionCompiler(
         fieldCompiler = this,
         scopedArguments = this.resolutionContext.activeScopes
      )
      // TODO :
      // We need to refactor how we're getting type for a field
      // There's a few different ways - and at the moment we don't verify that they're not conflicting.
      // A type can be declared:
      //  - explicitly :                                        foo : Foo
      //  - by the return type of an expression:                foo : returnFoo()
      //  - by a model reference:                               foo : Something::Foo
      //  - by a projection:                                    foo : returnBar() as { something : Foo }
      //  - A mixutre:                                          foo : Foo = returnFoo()
      //
      // This is current partly encapsulated through FieldTypeSpec, but
      // capturing it is awkward.
      val qualifiedName = fieldTypeDeclaration?.nullableTypeReference()?.typeReference()?.qualifiedName()
      return when {
         // Before resolving as a type, first check if we can resolve
         // through scope.
         // (eg: this.foo), or a named scope:
         // find { Foo[] } as (foo:Foo) -> { foo.xxxxx }
         fieldTypeDeclaration != null && expressionCompiler.canResolveAsScopePath(qualifiedName!!) -> {
            expressionCompiler.resolveScopePath(qualifiedName).flatMap { scopePathExpression ->
               val fieldTypeSpec = FieldTypeSpec.forExpression(scopePathExpression)
               parseFieldProjection(member, fieldTypeSpec).flatMap { fieldProjectionType ->
                  toField(
                     member,
                     namespace,
                     fieldTypeSpec,
                     typeDoc,
                     fieldAnnotations,
                     null,
                     FieldProjection.forNullable(fieldTypeSpec.type, fieldProjectionType),
                  )

               }
            }
         }
         // It wasn't a path, so resolve as a function
         fieldTypeDeclaration != null -> {
            tokenProcessor.parseType(namespace, fieldTypeDeclaration)
               .flatMap { fieldTypeSpec ->
                  parseFieldProjection(member, fieldTypeSpec).flatMap { fieldProjectionType ->
                     toField(
                        member,
                        namespace,
                        fieldTypeSpec,
                        typeDoc,
                        fieldAnnotations,
                        null,
                        FieldProjection.forNullable(fieldTypeSpec.type, fieldProjectionType),
                     )
                  }
               }
         }

         anonymousTypeDefinition != null -> {
            val typeName = anonymousTypeNameForMember(member)
            parseAnonymousTypeBody(typeName, anonymousTypeDefinition, resolutionContext)
               .flatMap { type ->
                  val fieldTypeSpec = FieldTypeSpec.forType(type)
                  parseFieldProjection(member, fieldTypeSpec).flatMap { projectionType ->
                     toField(
                        member,
                        namespace,
                        fieldTypeSpec,
                        typeDoc,
                        fieldAnnotations,
                        null,
                        FieldProjection.forNullable(fieldTypeSpec.type, projectionType)
                     )
                  }

               }
         }

         modelAttributeType != null -> {
            this.parseModelAttributeTypeReference(namespace, modelAttributeType)
               .flatMap { (memberSourceType, memberType) ->
                  toField(
                     member,
                     namespace,
                     FieldTypeSpec.forType(memberType),
                     typeDoc,
                     fieldAnnotations,
                     memberSourceType
                  )
               }
         }

         expressionGroup != null -> {
            expressionCompiler.compile(expressionGroup)
               .flatMap { expression ->
                  val fieldTypeSpec = FieldTypeSpec.forExpression(expression)
                  parseFieldProjection(member, fieldTypeSpec).flatMap { fieldProjectionType ->
                     toField(
                        member,
                        namespace,
                        fieldTypeSpec,
                        typeDoc,
                        fieldAnnotations,
                        null,
                        FieldProjection.forNullable(fieldTypeSpec.type, fieldProjectionType)
                     )
                  }

               }
         }

         else -> {
            // case for the following anonymous type definition as part of a query.
            // It implies that Foo has a 'tradeId' field.
            // findAll { Foo[] } as
            // {
            //   tradeId
            // }[]
            val fieldName = member.fieldDeclaration().identifier().text
            val discoveryTypes = resolutionContext.typesToDiscover
            if (discoveryTypes.isEmpty()) {
               listOf(
                  CompilationError(
                     member.start,
                     "The type for $fieldName can not be resolved without a query context"
                  )
               ).left()
            } else {
               val typeOrError =
                  this.tokenProcessor.getType(
                     namespace,
                     discoveryTypes.first().typeName.firstTypeParameterOrSelf,
                     member
                  )
               typeOrError.flatMap { type ->
                  when {
                     type !is ObjectType || !type.hasField(fieldName) ->
                        listOf(
                           CompilationError(
                              member.start,
                              "$typeName should be an object type containing field $fieldName"
                           )
                        ).left()

                     else -> type.field(fieldName).right()
                  }
               }
            }
         }
      }
   }

   private fun resolveImplicitTypeFromToBeProjectedType(
      member: TaxiParser.TypeMemberDeclarationContext
   ): Either<List<CompilationError>, Field> {
      val fieldName = member.fieldDeclaration().identifier().text
      return if (typeBody.objectType?.hasField(fieldName) == true) {
         typeBody.objectType!!.field(fieldName).right()
      } else {
         listOf(
            CompilationError(
               member.start,
               "Field ${
                  member.fieldDeclaration().identifier().text
               } does not have a type and cannot be found on the type being projected (${typeBody.objectType?.qualifiedName})."
            )
         ).left()
      }
   }

   fun anonymousTypeNameForMember(member: TaxiParser.TypeMemberDeclarationContext): String {
      return "$typeName$${
         member.fieldDeclaration().identifier().text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
               Locale.getDefault()
            ) else it.toString()
         }
      }"
   }

   private fun parseAnonymousTypeBody(
      anonymousTypeName: String,
      anonymousTypeDefinition: TaxiParser.AnonymousTypeDefinitionContext,
      resolutionContext: ResolutionContext
   ): Either<List<CompilationError>, Type> {
      val fieldType = tokenProcessor.parseAnonymousType(
         anonymousTypeDefinition.findNamespace(),
         anonymousTypeDefinition,
         anonymousTypeName,
         resolutionContext
      )
         .map { type ->
            val isDeclaredAsCollection = anonymousTypeDefinition.arrayMarker() != null
            if (isDeclaredAsCollection) {
               ArrayType.of(type, anonymousTypeDefinition.toCompilationUnit())
            } else {
               type
            }
         }
      return fieldType
   }

   private fun parseFieldProjection(
      member: TaxiParser.TypeMemberDeclarationContext,
      projectionSourceType: FieldTypeSpec,
   ): Either<List<CompilationError>, Pair<Type, ProjectionFunctionScope>?> {
      val typeProjection = member.fieldDeclaration().typeProjection() ?: return null.right()
      val typeName = anonymousTypeNameForMember(member)
      return parseFieldProjection(typeProjection, projectionSourceType, typeName)
   }


   fun parseFieldProjection(
      typeProjection: TypeProjectionContext,
      projectionSourceType: FieldTypeSpec,
      anonymousTypeName: String
   ): Either<List<CompilationError>, Pair<Type, ProjectionFunctionScope>> {
      return tokenProcessor.parseProjectionScope(typeProjection.expressionInputs(), projectionSourceType)
         .flatMap { projectionScope ->
            val projectedType = when {
               typeProjection.anonymousTypeDefinition() != null -> parseAnonymousTypeBody(
                  anonymousTypeName,
                  typeProjection.anonymousTypeDefinition(),
                  this.resolutionContext.appendScope(projectionScope)
               )

               typeProjection.typeReference() != null -> tokenProcessor.typeOrError(typeProjection.typeReference())
               else -> error("Can't lookup type reference for projection from statement: ${typeProjection.source().content}")
            }
            projectedType.map { type -> type to projectionScope }
         }
   }

   private fun toField(
      member: TaxiParser.TypeMemberDeclarationContext,
      namespace: Namespace,
      fieldType: FieldTypeSpec,
      typeDoc: String?,
      fieldAnnotations: List<Annotation>,
      memberSource: QualifiedName? = null,
      fieldProjection: FieldProjection? = null
   ): Either<List<CompilationError>, Field> {
      val format = tokenProcessor.parseTypeFormat(fieldAnnotations, fieldType.type, member)
         .getOrElse {
            errors.addAll(it)
            null
         }
      return when {
         // orderId:  Order::OrderSentId
         memberSource != null -> {
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
               type = fieldProjection?.projectedType ?: fieldType.type,
               fieldProjection = fieldProjection,
               nullable = false,
               modifiers = emptyList(),
               annotations = fieldAnnotations,
               constraints = emptyList(),
               accessor = null,
               typeDoc = typeDoc,
               memberSource = memberSource,
               fieldFormat = format,

//               projectionScopeTypes = projectionScopeTypes,
               compilationUnit = member.fieldDeclaration().toCompilationUnit()
            ).right()
         }

         // trader: { traderId: TraderId, traderName: TraderName }
         member.fieldDeclaration().anonymousTypeDefinition() != null -> {
            val accessor =
               compileAccessor(member.fieldDeclaration().anonymousTypeDefinition().accessor(), fieldType.type)
                  ?.getOrElse {
                     errors.addAll(it)
                     null
                  }
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
               type = fieldProjection?.projectedType ?: fieldType.type,
               fieldProjection = fieldProjection,
               nullable = false,
               modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
               annotations = fieldAnnotations,
               constraints = emptyList(),
               accessor = accessor,
               typeDoc = typeDoc,
               compilationUnit = member.fieldDeclaration().toCompilationUnit(),
               memberSource = memberSource,
               fieldFormat = format,
//               projectionScopeTypes = projectionScopeTypes,
            ).right()

         }

         // orderId: OrderId
         else -> {
            val fieldDeclaration: TaxiParser.FieldTypeDeclarationContext? =
               member.fieldDeclaration().fieldTypeDeclaration()
            val accessor = fieldDeclaration?.accessor()?.let { accessorContext ->
               compileAccessor(accessorContext, fieldType.type)
            }?.getOrElse {
               errors.addAll(it)
               null
            }
            val simpleType = fieldDeclaration?.nullableTypeReference()
            if (fieldType.accessor != null && accessor != null) {
               error("It is invalid for both the field to define an inferred accessor and an explict accessor.  Shouldn't happen")
            }
            tokenProcessor.mapConstraints(
               fieldDeclaration?.parameterConstraint()?.expressionGroup(),
               fieldType.type,
               this,
               this.resolutionContext.activeScopes
            ).map { constraints ->
               Field(
                  name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
                  type = fieldProjection?.projectedType ?: fieldType.type,
                  fieldProjection = fieldProjection,
                  nullable = simpleType?.Nullable() != null,
                  modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
                  annotations = fieldAnnotations,
                  constraints = constraints,
                  accessor = accessor ?: fieldType.accessor,
                  typeDoc = typeDoc,
                  fieldFormat = format,
//                  projectionScopeTypes = projectionScopeTypes,
                  compilationUnit = member.fieldDeclaration().toCompilationUnit()
               )
            }
         }
      }
   }

   /**
    * Returns the list of type names that should be used to define the scope for projecting the defined
    * field in a complex projection.
    *
    * eg:
    * findAll { Transaction[] } as {
    *    items : TransactionItem -> {
    *       sku : ProductSku
    *       size : ProductSize
    *    }[]
    * }[]
    *
    * Note: Currently the syntax only supports defining a single value here, but we plan to expand this
    * to a collection in the near future, so return a List<Type> rather than Type
    */
//   private fun getFieldProjectionScope(member: TaxiParser.TypeMemberDeclarationContext): Either<List<CompilationError>, List<Type>> =
//      member.fieldDeclaration().collectionProjectionScope()?.typeReference()?.let { typeType ->
//         this.tokenProcessor.typeOrError(typeType.findNamespace(), typeType)
//               .map { listOf(it) }
//      } ?: Either.right(emptyList())

   private fun mapFieldModifiers(fieldModifier: TaxiParser.FieldModifierContext?): List<FieldModifier> {
      if (fieldModifier == null) return emptyList()
      val modifier = FieldModifier.values().firstOrNull { it.token == fieldModifier.text }
         ?: error("Unknown field modifier: ${fieldModifier.text}")
      return listOf(modifier)
   }


   fun compileAccessor(
      accessorContext: TaxiParser.AccessorContext?,
      targetType: Type
   ): Either<List<CompilationError>, Accessor>? {
      return when {
         accessorContext == null -> null
         accessorContext.scalarAccessorExpression() != null -> compileScalarAccessor(
            accessorContext.scalarAccessorExpression(),
            targetType
         ).flatMap { accessor ->
            // Do type checks..
            accessor.strictReturnType
               .mapLeft { errorMessage -> CompilationError(accessorContext.start, errorMessage) }
               .flatMap { returnType ->
                  val message = typeChecker.assertIsAssignable(
                     accessor.returnType,
                     targetType,
                     accessorContext.scalarAccessorExpression()
                  )
                  message?.left() ?: accessor.right()
               }
               .wrapErrorsInList()
         }

         else -> null
      }
   }

   internal fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type = PrimitiveType.ANY
   ): Either<List<CompilationError>, Accessor> {
      if (targetType == PrimitiveType.ANY) {
         log().warn("Type was provided as Any, not performing type checks")
      }
      return when {
         expression.jsonPathAccessorDeclaration() != null -> JsonPathAccessor(
            path = expression.jsonPathAccessorDeclaration().StringLiteral().text.removeSurrounding("\""),
            returnType = targetType
         ).right()

         expression.xpathAccessorDeclaration() != null -> XpathAccessor(
            expression.xpathAccessorDeclaration().StringLiteral().text.removeSurrounding("\""),
            returnType = targetType
         ).right()

         expression.columnDefinition() != null -> {
            ColumnAccessor(
               index =
               expression.columnDefinition().columnIndex().StringLiteral()?.text
                  ?: expression.columnDefinition().columnIndex().IntegerLiteral().text.toInt(),
               returnType = targetType
            ).right()
         }

//         expression.conditionalTypeConditionDeclaration() != null -> {
//            val namespace = expression.conditionalTypeConditionDeclaration().findNamespace()
//            conditionalFieldSetProcessor.compileCondition(
//               expression.conditionalTypeConditionDeclaration(),
//               namespace,
//               targetType
//            )
//               .map { condition -> ConditionalAccessor(condition) }
//         }

//         expression.defaultDefinition() != null -> {
//            val defaultValue = defaultValueParser.parseDefaultValue(expression.defaultDefinition(), targetType)
//               .collectError(errors).getOrElse { null }
//            ColumnAccessor(
//               index = null,
//               defaultValue = defaultValue,
//               returnType = targetType
//            ).right()
//         }

//         expression.functionCall() != null -> {
//            val functionContext = expression.functionCall()
//            buildReadFunctionAccessor(functionContext, targetType)
//         }

         expression.expressionGroup() != null -> buildReadFunctionExpressionAccessor(
            expression.expressionGroup(),
            targetType
         )
//          Spike: Removing unused grammar elements
         expression.byFieldSourceExpression() != null -> buildReadFieldAccessor(expression.byFieldSourceExpression())
//         expression.collectionProjectionExpression() != null -> buildCollectionProjectionExpression(expression.collectionProjectionExpression())
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun buildCollectionProjectionExpression(collectionProjectionExpression: TaxiParser.CollectionProjectionExpressionContext): Either<List<CompilationError>, Accessor> {
      return this.tokenProcessor.typeOrError(
         collectionProjectionExpression.findNamespace(),
         collectionProjectionExpression.typeReference()
      )
         .flatMap { type ->
            val scopeOrError: Either<List<CompilationError>, ProjectionScopeDefinition?> =
               if (collectionProjectionExpression.projectionScopeDefinition() != null) {
                  compileProjectionScope(collectionProjectionExpression.projectionScopeDefinition())
               } else null.right()
            scopeOrError.map { scope ->
               CollectionProjectionExpressionAccessor(
                  type,
                  scope,
                  collectionProjectionExpression.toCompilationUnits()
               )
            }
         }
   }

   private fun compileProjectionScope(projectionScopeDefinition: TaxiParser.ProjectionScopeDefinitionContext): Either<List<CompilationError>, ProjectionScopeDefinition> {
      val accessors: Either<List<CompilationError>, List<Accessor>> =
         projectionScopeDefinition.scalarAccessorExpression().map { accessor ->
            compileScalarAccessor(accessor, targetType = PrimitiveType.ANY)
         }.invertEitherList()
            .flattenErrors()

      return accessors.map { ProjectionScopeDefinition(it) }

   }

   private fun buildReadFieldAccessor(byFieldSourceExpression: TaxiParser.ByFieldSourceExpressionContext): Either<List<CompilationError>, Accessor> {
      //as {
      //      traderEmail: SomeType['traderId']
      //}

      val referencedFieldName = stringLiteralValue(byFieldSourceExpression.StringLiteral()) // e.g. traderId
      return if (this.resolutionContext.concreteProjectionTypeContext != null) {
         /**
          * Example: here our anonymous type extends foo.Trade
          * findAll {
         Order[]( TradeDate  >= startDate , TradeDate < endDate )
         } as foo.Trade {
         traderEmail: UserEmail by foo.Trade['traderId']
         }[]
          */
         val sourceTypeOrError = this.tokenProcessor.typeOrError( // e.g. SomeType
            byFieldSourceExpression.findNamespace(),
            byFieldSourceExpression.typeReference()
         )

         sourceTypeOrError.flatMap { sourceType ->
            val baseProjectionTypeOrError = this.tokenProcessor.typeOrError(
               byFieldSourceExpression.findNamespace(),
               this.resolutionContext.concreteProjectionTypeContext
            )

            baseProjectionTypeOrError.flatMap { baseProjectionType ->
               if (baseProjectionType == sourceType) {
                  createFieldSource(
                     baseProjectionTypeOrError,
                     byFieldSourceExpression,
                     referencedFieldName,
                     QualifiedName.from(typeName)
                  )
               } else {
                  createFieldSource(
                     sourceTypeOrError,
                     byFieldSourceExpression,
                     referencedFieldName
                  )
               }
            }
         }
      } else {
         val sourceType = this.tokenProcessor.typeOrError( // e.g. SomeType
            byFieldSourceExpression.findNamespace(),
            byFieldSourceExpression.typeReference()
         )

         createFieldSource(
            sourceType,
            byFieldSourceExpression,
            referencedFieldName
         )
      }
   }

   private fun createFieldSource(
      sourceType: Either<List<CompilationError>, Type>,
      byFieldSourceExpression: TaxiParser.ByFieldSourceExpressionContext,
      referencedFieldName: String,
      sourceTypeName: QualifiedName? = null
   ): Either<List<CompilationError>, Accessor> {
      return when (sourceType) {
         is Either.Left -> return sourceType.value.left()
         is Either.Right -> {
            if (sourceType.value !is ObjectType) {
               listOf(
                  CompilationError(
                     byFieldSourceExpression.start,
                     "${sourceType.value.qualifiedName} must be an ObjectType",
                     byFieldSourceExpression.source().sourceName
                  )
               ).left()
            }

            val objectType = sourceType.value as ObjectType
            if (!objectType.hasField(referencedFieldName)) {
               listOf(
                  CompilationError(
                     byFieldSourceExpression.start,
                     "${sourceType.value.qualifiedName} should have a field called $referencedFieldName",
                     byFieldSourceExpression.source().sourceName
                  )
               ).left()
            }

            FieldSourceAccessor(
               sourceAttributeName = referencedFieldName,
               attributeType = objectType.field(referencedFieldName).type.toQualifiedName(),
               sourceType = sourceTypeName ?: objectType.toQualifiedName()
            ).right()
         }
      }
   }

   private fun buildReadFunctionExpressionAccessor(
      readExpressionContext: TaxiParser.ExpressionGroupContext,
      targetType: Type
   ): Either<List<CompilationError>, out Accessor> {
      val expression = ExpressionCompiler(this.tokenProcessor, this.typeChecker, this.errors, this)
         .compile(readExpressionContext, targetType)
      return expression
   }


   fun typeResolver(namespace: Namespace) = tokenProcessor.typeResolver(namespace)

   fun typeOrError(context: TypeReferenceContext) = tokenProcessor.typeOrError(context)
   fun parseType(namespace: Namespace, typeType: TypeReferenceContext) =
      tokenProcessor.parseType(namespace, typeType)

   fun parseModelAttributeTypeReference(
      namespace: Namespace,
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, Pair<QualifiedName, Type>> =
      tokenProcessor.parseModelAttributeTypeReference(namespace, modelAttributeReferenceCtx)
}
