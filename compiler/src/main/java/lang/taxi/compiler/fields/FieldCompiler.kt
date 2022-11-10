package lang.taxi.compiler.fields

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.CollectionProjectionExpressionAccessor
import lang.taxi.accessors.ColumnAccessor
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.FieldSourceAccessor
import lang.taxi.accessors.JsonPathAccessor
import lang.taxi.accessors.ProjectionScopeDefinition
import lang.taxi.accessors.XpathAccessor
import lang.taxi.compiler.AnonymousTypeResolutionContext
import lang.taxi.compiler.ConditionalFieldSetProcessor
import lang.taxi.compiler.DefaultValueParser
import lang.taxi.compiler.ExpressionCompiler
import lang.taxi.compiler.TokenProcessor
import lang.taxi.compiler.assertIsAssignable
import lang.taxi.compiler.collectError
import lang.taxi.compiler.collectErrors
import lang.taxi.findNamespace
import lang.taxi.source
import lang.taxi.stringLiteralValue
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.Field
import lang.taxi.types.FieldModifier
import lang.taxi.types.FieldProjection
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
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
   private val anonymousTypeResolutionContext: AnonymousTypeResolutionContext = AnonymousTypeResolutionContext()
) {
   internal val typeChecker = tokenProcessor.typeChecker
   private val conditionalFieldSetProcessor =
      ConditionalFieldSetProcessor(this, ExpressionCompiler(tokenProcessor, typeChecker, errors, this))

   //   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)
   private val defaultValueParser = DefaultValueParser()

   private val fieldsBeingCompiled = mutableSetOf<String>()
   private val compiledFields = mutableMapOf<String, Either<List<CompilationError>, Field>>()

   private val fieldNamesToDefinitions: Map<String, TaxiParser.TypeMemberDeclarationContext> by lazy {
      fun getFieldNameAndDeclarationContext(memberDeclaration: TaxiParser.TypeMemberDeclarationContext): Pair<String, TaxiParser.TypeMemberDeclarationContext> {
         return TokenProcessor.unescape(memberDeclaration.fieldDeclaration().identifier().text) to memberDeclaration
      }

      val fields = typeBody.memberDeclarations
         .map { getFieldNameAndDeclarationContext(it) }
         .toList()

      val fieldsInFieldBlocks = typeBody.conditionalTypeDeclarations.flatMap { fieldBlock ->
         fieldBlock.typeMemberDeclaration().map { memberDeclarationContext ->
            getFieldNameAndDeclarationContext(memberDeclarationContext)
         }
      }.toList()

      // Check for duplicate field declarations
      val allFields = fields + fieldsInFieldBlocks
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
      val namespace = typeBody.findNamespace()
      val conditionalFieldStructures = typeBody.conditionalTypeDeclarations.mapNotNull { conditionalFieldBlock ->
         conditionalFieldSetProcessor.compileConditionalFieldStructure(conditionalFieldBlock, namespace)
            .collectErrors(errors).getOrElse { null }
      }

      val fieldsWithConditions = conditionalFieldStructures.flatMap { it.fields }

      val fields = buildObjectFields(typeBody.memberDeclarations)

      val spreadFields = buildSpreadFieldsIfEnabled()

      return (fields + fieldsWithConditions + spreadFields).distinctBy { it.name }
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

      return typeBody.objectType?.fields ?: emptyList()
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
      val fieldType = member.fieldDeclaration().fieldTypeDeclaration()
      val isChildOfProjection = typeBody.objectType?.qualifiedName != null
      if (fieldType == null && isChildOfProjection) {
         return resolveImplicitTypeFromToBeProjectedType(member)
      }
      // orderId: {  foo: String }
      val anonymousTypeDefinition = member.fieldDeclaration().anonymousTypeDefinition()
      // orderId: Order::OrderId
      val modelAttributeType = member.fieldDeclaration().modelAttributeTypeReference()
      val expressionGroup = member.fieldDeclaration().expressionGroup()

      val fieldProjectionType: Either<List<CompilationError>, Type?> =
         member.fieldDeclaration().typeProjection()?.let { projection -> parseFieldProjection(member, projection) }
            ?: (null as Type?).right()

      return when {
         fieldType != null -> {
            tokenProcessor.parseType(namespace, fieldType)
               .flatMap { fieldTypeSpec ->
                  fieldProjectionType.flatMap { projectionType ->
                     toField(
                        member,
                        namespace,
                        fieldTypeSpec,
                        typeDoc,
                        fieldAnnotations,
                        fieldProjectionType = projectionType
                     )
                  }
               }
         }

         anonymousTypeDefinition != null -> {
            parseAnonymousTypeBody(member, anonymousTypeDefinition)
               .flatMap { type ->
                  fieldProjectionType.flatMap { projectionType ->
                     toField(
                        member,
                        namespace,
                        FieldTypeSpec.forType(type),
                        typeDoc,
                        fieldAnnotations,
                        fieldProjectionType = projectionType
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
            tokenProcessor.expressionCompiler(fieldCompiler = this).compile(expressionGroup)
               .flatMap { expression ->
                  fieldProjectionType.flatMap { fieldProjectionType ->
                     toField(
                        member,
                        namespace,
                        FieldTypeSpec.forExpression(expression),
                        typeDoc,
                        fieldAnnotations,
                        null,
                        fieldProjectionType
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
            val discoveryTypes = anonymousTypeResolutionContext.typesToDiscover
            if (discoveryTypes.isEmpty()) {

               listOf(
                  CompilationError(
                     member.start,
                     "The type for $fieldName can not be resolved without a query context"
                  )
               ).left()
            } else {
               val typeOrError =
                  this.tokenProcessor.getType(namespace, discoveryTypes.first().type.firstTypeParameterOrSelf, member)
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

   private fun parseAnonymousTypeBody(
      member: TaxiParser.TypeMemberDeclarationContext,
      anonymousTypeDefinition: TaxiParser.AnonymousTypeDefinitionContext,
   ): Either<List<CompilationError>, Type> {
      val anonymousTypeName = "$typeName$${member.fieldDeclaration().identifier().text.replaceFirstChar {
         if (it.isLowerCase()) it.titlecase(
            Locale.getDefault()
         ) else it.toString()
      }}"
      val fieldType = tokenProcessor.parseAnonymousType(
         anonymousTypeDefinition.findNamespace(),
         anonymousTypeDefinition,
         anonymousTypeName
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
      projection: TaxiParser.TypeProjectionContext
   ): Either<List<CompilationError>, Type> {
      return parseAnonymousTypeBody(
         member,
         projection.anonymousTypeDefinition()
      )
   }


   private fun toField(
      member: TaxiParser.TypeMemberDeclarationContext,
      namespace: Namespace,
      fieldType: FieldTypeSpec,
      typeDoc: String?,
      fieldAnnotations: List<Annotation>,
      memberSource: QualifiedName? = null,
      fieldProjectionType: Type? = null
   ): Either<List<CompilationError>, Field> {
      val format = tokenProcessor.parseTypeFormat(fieldAnnotations, fieldType.type, member)
         .getOrHandle {
            errors.addAll(it)
            null
         }
      val fieldProjection =
         fieldProjectionType?.let { projectionType -> FieldProjection(fieldType.type, projectionType) }
      return when {
         // orderId:  Order::OrderSentId
         memberSource != null -> {
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
               type = fieldProjection?.projectedType ?: fieldType.type,
               projection = fieldProjection,
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
                  ?.getOrHandle {
                     errors.addAll(it)
                     null
                  }
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
               type = fieldProjection?.projectedType ?: fieldType.type,
               projection = fieldProjection,
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
            }?.getOrHandle {
               errors.addAll(it)
               null
            }
            val simpleType = fieldDeclaration?.optionalTypeReference()
            if (fieldType.accessor != null && accessor != null) {
               error("It is invalid for both the field to define an inferred accessor and an explict accessor.  Shouldn't happen")
            }
            if (fieldDeclaration?.parameterConstraint()?.parameterConstraintExpressionList() != null) {
               error("parameterConstraintExpressionList on a field has been replaced, and shouldn't be possible anymore.  Understand how we got here.  Syntax in question: ${fieldDeclaration.source().content}")
            }
            tokenProcessor.mapConstraints(
               fieldDeclaration?.parameterConstraint()?.expressionGroup(),
               fieldType.type,
               this
            ).map { constraints ->
               Field(
                  name = TokenProcessor.unescape(member.fieldDeclaration().identifier().text),
                  type = fieldProjection?.projectedType ?: fieldType.type,
                  projection = fieldProjection,
                  nullable = simpleType?.optionalType() != null,
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
               defaultValue = null,
               returnType = targetType
            ).right()
         }

         expression.conditionalTypeConditionDeclaration() != null -> {
            val namespace = expression.conditionalTypeConditionDeclaration().findNamespace()
            conditionalFieldSetProcessor.compileCondition(
               expression.conditionalTypeConditionDeclaration(),
               namespace,
               targetType
            )
               .map { condition -> ConditionalAccessor(condition) }
         }

         expression.defaultDefinition() != null -> {
            val defaultValue = defaultValueParser.parseDefaultValue(expression.defaultDefinition(), targetType)
               .collectError(errors).getOrElse { null }
            ColumnAccessor(
               index = null,
               defaultValue = defaultValue,
               returnType = targetType
            ).right()
         }

//         expression.functionCall() != null -> {
//            val functionContext = expression.functionCall()
//            buildReadFunctionAccessor(functionContext, targetType)
//         }

         expression.expressionGroup() != null -> buildReadFunctionExpressionAccessor(
            expression.expressionGroup(),
            targetType
         )

         expression.byFieldSourceExpression() != null -> buildReadFieldAccessor(expression.byFieldSourceExpression())
         expression.collectionProjectionExpression() != null -> buildCollectionProjectionExpression(expression.collectionProjectionExpression())
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
      return if (this.anonymousTypeResolutionContext.concreteProjectionTypeContext != null) {
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
               this.anonymousTypeResolutionContext.concreteProjectionTypeContext
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
         .compile(readExpressionContext)
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
