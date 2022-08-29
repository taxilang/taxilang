package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.CollectionProjectionExpressionAccessor
import lang.taxi.accessors.ColumnAccessor
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.FieldSourceAccessor
import lang.taxi.accessors.JsonPathAccessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.accessors.ProjectionScopeDefinition
import lang.taxi.accessors.XpathAccessor
import lang.taxi.findNamespace
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionModifiers
import lang.taxi.source
import lang.taxi.stringLiteralValue
import lang.taxi.text
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.Field
import lang.taxi.types.FieldModifier
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.value
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext

/**
 * Wrapper interface for "Things we need to compile fields from".
 * We compile either for a type body, or an annotation body.
 */
interface TypeWithFieldsContext {
   fun findNamespace(): String
   fun memberDeclaration(
      fieldName: String,
      compilingTypeName: String,
      requestingToken: ParserRuleContext
   ): Either<List<CompilationError>, TaxiParser.TypeMemberDeclarationContext> {
      val memberDeclaration = this.memberDeclarations
         .firstOrNull { TokenProcessor.unescape(it.fieldDeclaration().Identifier().text) == fieldName }

      return memberDeclaration.rightIfNotNull {
         listOf(CompilationError(requestingToken.start, "Field $fieldName does not exist on type $compilingTypeName"))
      }
   }

   val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
   val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
   val parent: RuleContext?
}

class AnnotationTypeBodyContent(private val typeBody: TaxiParser.AnnotationTypeBodyContext?, val namespace: String) :
   TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext> = emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()
   override val parent: RuleContext?
      get() = typeBody?.parent
}

class TypeBodyContext(private val typeBody: TaxiParser.TypeBodyContext?, val namespace: String) :
   TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
      get() = typeBody?.conditionalTypeStructureDeclaration() ?: emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()
   override val parent: RuleContext?
      get() = typeBody?.parent

}

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
         return TokenProcessor.unescape(memberDeclaration.fieldDeclaration().Identifier().text) to memberDeclaration
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

      val fields = typeBody.memberDeclarations.map { member ->
         provideField(TokenProcessor.unescape(member.fieldDeclaration().Identifier().text), member)
      }.mapNotNull { either -> either.collectErrors(errors).getOrElse { null } }
      return fields + fieldsWithConditions
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

//   internal fun compiledField(member: TaxiParser.TypeMemberDeclarationContext): Field? {
//      return compileField(member)
//         .collectErrors()
//         .orNull()
//   }

   private fun compileField(member: TaxiParser.TypeMemberDeclarationContext): Either<List<CompilationError>, Field> {
      val fieldAnnotations = tokenProcessor.collateAnnotations(member.annotation())

      val typeDoc = tokenProcessor.parseTypeDoc(member.typeDoc())
      val namespace = member.findNamespace()
      // orderId: String
      val simpleType = member.fieldDeclaration().simpleFieldDeclaration()?.typeType()
      // orderId: {  foo: String }
      val anonymousTypeDefinition = member.fieldDeclaration().anonymousTypeDefinition()
      // orderId: Order::OrderId
      val modelAttributeType = member.fieldDeclaration().modelAttributeTypeReference()
      return when {
         simpleType != null -> {
            val fieldType = tokenProcessor.parseType(namespace, simpleType)
            fieldType.flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
         }

         anonymousTypeDefinition != null -> {
            val anonymousTypeBody = anonymousTypeDefinition.typeBody()

            val anonymousTypeName = "$typeName$${member.fieldDeclaration().Identifier().text.capitalize()}"
            val fieldType = tokenProcessor.parseAnonymousType(namespace, anonymousTypeDefinition, anonymousTypeName)
               .map { type ->
                  val isDeclaredAsCollection = anonymousTypeDefinition.listType() != null
                  if (isDeclaredAsCollection) {
                     ArrayType.of(type, anonymousTypeBody.toCompilationUnit())
                  } else {
                     type
                  }
               }
            fieldType.flatMap { type ->

               toField(member, namespace, type, typeDoc, fieldAnnotations)
            }
         }

         modelAttributeType != null -> {
            this.parseModelAttributeTypeReference(namespace, modelAttributeType)
               .flatMap { (memberSourceType, memberType) ->
                  toField(member, namespace, memberType, typeDoc, fieldAnnotations, memberSourceType)
               }
         }

         else -> {
            // case for the following anonymous type definition as part of a query.
            // It implies that Foo has a 'tradeId' field.
            // findAll { Foo[] } as
            // {
            //   tradeId
            // }[]
            val fieldName = member.fieldDeclaration().Identifier().text
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
                     type !is ObjectType ->
                        listOf(
                           CompilationError(
                              member.start,
                              "$typeName should be an object type containing field $fieldName"
                           )
                        ).left()

                     !type.hasField(fieldName) ->
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


   private fun toField(
      member: TaxiParser.TypeMemberDeclarationContext,
      namespace: Namespace,
      type: Type,
      typeDoc: String?,
      fieldAnnotations: List<Annotation>,
      memberSource: QualifiedName? = null
   ): Either<List<CompilationError>, Field> {
      return when {
         // orderId:  Order::OrderSentId
         memberSource != null -> {
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().Identifier().text),
               type = type,
               nullable = false,
               modifiers = emptyList(),
               annotations = fieldAnnotations,
               constraints = emptyList(),
               accessor = null,
               typeDoc = typeDoc,
               memberSource = memberSource,
//               projectionScopeTypes = projectionScopeTypes,
               compilationUnit = member.fieldDeclaration().toCompilationUnit()
            ).right()
         }

         // trader: { traderId: TraderId, traderName: TraderName }
         member.fieldDeclaration().anonymousTypeDefinition() != null -> {
            val accessor = compileAccessor(member.fieldDeclaration().anonymousTypeDefinition().accessor(), type)
               ?.getOrHandle {
                  errors.addAll(it)
                  null
               }
            Field(
               name = TokenProcessor.unescape(member.fieldDeclaration().Identifier().text),
               type = type,
               nullable = false,
               modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
               annotations = fieldAnnotations,
               constraints = emptyList(),
               accessor = accessor,
               typeDoc = typeDoc,
               compilationUnit = member.fieldDeclaration().toCompilationUnit(),
               memberSource = memberSource,
//               projectionScopeTypes = projectionScopeTypes,
            ).right()

         }

         // orderId: OrderId
         else -> {
            val fieldDeclaration = member.fieldDeclaration().simpleFieldDeclaration()
            val accessor = fieldDeclaration.accessor()?.let { accessorContext ->
               compileAccessor(accessorContext, type)
            }?.getOrHandle {
               errors.addAll(it)
               null
            }
            val simpleType = fieldDeclaration.typeType()
            tokenProcessor.mapConstraints(simpleType, type, namespace).map { constraints ->
               Field(
                  name = TokenProcessor.unescape(member.fieldDeclaration().Identifier().text),
                  type = type,
                  nullable = simpleType.optionalType() != null,
                  modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
                  annotations = fieldAnnotations,
                  constraints = constraints,
                  accessor = accessor,
                  typeDoc = typeDoc,
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
//      member.fieldDeclaration().collectionProjectionScope()?.typeType()?.let { typeType ->
//         this.tokenProcessor.typeOrError(typeType.findNamespace(), typeType)
//               .map { listOf(it) }
//      } ?: Either.right(emptyList())

   private fun mapFieldModifiers(fieldModifier: TaxiParser.FieldModifierContext?): List<FieldModifier> {
      if (fieldModifier == null) return emptyList()
      val modifier = FieldModifier.values().firstOrNull { it.token == fieldModifier.text }
         ?: error("Unknown field modifier: ${fieldModifier.text}")
      return listOf(modifier)
   }

//  CalculatedFields replaced by accessors
//   internal fun compileCalculatedField(member: TaxiParser.TypeMemberDeclarationContext,
//                                       formula: Formula,
//                                       namespace: Namespace): Either<List<CompilationError>, Field> {
//      val fieldAnnotations = tokenProcessor.collateAnnotations(member.annotation())
//      val typeDoc = tokenProcessor.parseTypeDoc(member.typeDoc())
//      return tokenProcessor.parseType(namespace, formula, member.fieldDeclaration().simpleFieldDeclaration().typeType())
//         .flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
//   }

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
//
//   private fun compileDestructuredAccessor(block: TaxiParser.ObjectAccessorContext, targetType: Type): DestructuredAccessor? {
//      if (targetType !is ObjectType) {
//         this.errors.add(CompilationError(block.start, "Destructuring is not permitted here because ${targetType.qualifiedName} is not an object type"))
//         return null
//      }
//
//      val accessorErrors = mutableListOf<CompilationError>()
//      val fields = block.destructuredFieldDeclaration().mapNotNull { fieldDeclaration ->
//         val fieldName = fieldDeclaration.Identifier().text
//         if (!targetType.hasField(fieldName)) {
//            accessorErrors.add(CompilationError(fieldDeclaration.start, "${targetType.qualifiedName} has no field called $fieldName"))
//            return@mapNotNull null
//         }
//         val fieldType = targetType.field(fieldName).type
//         val fieldNameToAccessor = compileAccessor(fieldDeclaration.accessor(), fieldType).flatMap { accessor: Accessor? ->
//            if (accessor == null) {
//               listOf(CompilationError(fieldDeclaration.start, "Expected an accessor to be defined", block.source().sourceName)).left()
//            } else {
//               (fieldName to accessor).right()
//            }
//         }.getOrHandle { errors ->
//            accessorErrors.addAll(errors)
//            null
//         }
//         fieldNameToAccessor
//      }.toMap()
//      // TODO : Validate that the object is fully defined..
//      // No invalid fields declared
//      // No non-null fields omitted
//      return if (accessorErrors.isNotEmpty()) {
//         this.errors.addAll(accessorErrors)
//         null
//      } else {
//         DestructuredAccessor(fields)
//      }
//   }

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

         expression.readFunction() != null -> {
            val functionContext = expression.readFunction()
            buildReadFunctionAccessor(functionContext, targetType)
         }

         expression.readExpression() != null -> buildReadFunctionExpressionAccessor(
            expression.readExpression(),
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
         collectionProjectionExpression.typeType()
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
            byFieldSourceExpression.typeType()
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
            byFieldSourceExpression.typeType()
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
      readExpressionContext: TaxiParser.ReadExpressionContext,
      targetType: Type
   ): Either<List<CompilationError>, out Accessor> {
      val expression = ExpressionCompiler(this.tokenProcessor, this.typeChecker, this.errors, this)
         .compile(readExpressionContext.expressionGroup())
      return expression

//      val allowedFunctionReturnTypes = setOf(PrimitiveType.INTEGER, PrimitiveType.STRING)
//      val allowedOperationTypes = mapOf(
//         PrimitiveType.INTEGER to setOf(FormulaOperator.Add, FormulaOperator.Subtract, FormulaOperator.Multiply),
//         PrimitiveType.STRING to setOf(FormulaOperator.Add)
//      )
//      val allowedOperandTypes = mapOf<PrimitiveType, ((value: Any) -> Boolean)>(
//         PrimitiveType.INTEGER to { value -> value is Int },
//         PrimitiveType.STRING to { value -> value is String })
//
//      return buildReadFunctionAccessor(readExpressionContext.readFunction(), targetType).flatMap { readFunctionAccessor ->
//         val functionBaseReturnType = readFunctionAccessor.function.returnType?.basePrimitive
//         if (!allowedFunctionReturnTypes.contains(functionBaseReturnType)) {
//            throw CompilationException(CompilationError(
//               readExpressionContext.start,
//               "function needs to return one of these types => ${allowedFunctionReturnTypes.joinToString { it.qualifiedName }}",
//               readExpressionContext.source().sourceName))
//         }
//         val arithmeticOperator = FormulaOperator.forSymbol(readExpressionContext.arithmaticOperator().text)
//         if (!allowedOperationTypes[functionBaseReturnType]!!.contains(arithmeticOperator)) {
//            throw CompilationException(CompilationError(
//               readExpressionContext.start,
//               "only the following operations are allowed => ${allowedOperationTypes[functionBaseReturnType]!!.joinToString { it.symbol }}",
//               readExpressionContext.source().sourceName))
//         }
//         val operand = readExpressionContext.literal().value()
//         if (!allowedOperandTypes[functionBaseReturnType]!!(operand)) {
//            CompilationError(
//               readExpressionContext.start,
//               "$operand is not an allowed value for ${functionBaseReturnType?.qualifiedName}"
//            ).asList().left()
//         } else {
//            FunctionExpressionAccessor(
//               readFunctionAccessor,
//               FormulaOperator.forSymbol(readExpressionContext.arithmaticOperator().text),
//               readExpressionContext.literal().value()).right()
//         }
//
//      }
      TODO()


   }

   internal fun buildReadFunctionAccessor(
      functionContext: TaxiParser.ReadFunctionContext,
      targetType: Type
   ): Either<List<CompilationError>, FunctionAccessor> {
      val namespace = functionContext.findNamespace()
      return tokenProcessor.attemptToLookupTypeByName(
         namespace,
         functionContext.functionName().qualifiedName().Identifier().text(),
         functionContext,
         symbolKind = SymbolKind.FUNCTION
      )
         .wrapErrorsInList()
         .flatMap { qualifiedName ->
            tokenProcessor.resolveFunction(qualifiedName, functionContext).flatMap { function ->
               require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
               typeChecker.assertIsAssignable(function.returnType!!, targetType, functionContext)
                  ?.let { compilationError ->
                     errors.add(compilationError)
                  }

               functionContext.formalParameterList().parameter().mapIndexed { parameterIndex, parameterContext ->
                  val parameterType = function.getParameterType(parameterIndex)
                  if (parameterContext.scalarAccessorExpression() != null && parameterContext.scalarAccessorExpression()
                        .readExpression() != null && this.typeBody.parent.isInViewContext()
                  ) {
                     val errorOrExpression =
                        ExpressionCompiler(this.tokenProcessor, this.typeChecker, this.errors, this)
                           .compile(parameterContext.scalarAccessorExpression().readExpression().expressionGroup())
                     if (errorOrExpression is Either.Left) {
                        return@flatMap errorOrExpression.value.left()

                     }
                  }
                  val parameterAccessor = when {
                     parameterContext.literal() != null -> LiteralAccessor(
                        parameterContext.literal().value()
                     ).right()

                     parameterContext.scalarAccessorExpression() != null -> compileScalarAccessor(
                        parameterContext.scalarAccessorExpression(),
                        parameterType
                     )
//                  parameterContext.readFunction() !s= null -> buildReadFunctionAccessor(parameterContext.readFunction()).right()
//                  parameterContext.columnDefinition() != null -> buildColumnAccessor(parameterContext.columnDefinition()).right()
                     parameterContext.fieldReferenceSelector() != null -> compileFieldReferenceAccessor(
                        function,
                        parameterContext
                     ).right()

                     parameterContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(
                        namespace,
                        parameterContext
                     )

                     parameterContext.modelAttributeTypeReference() != null -> {
                        if (this.typeBody.parent.isInViewContext()) {
                           this.parseModelAttributeTypeReference(
                              namespace,
                              parameterContext.modelAttributeTypeReference()
                           )
                              .flatMap { (memberSourceType, memberType) ->
                                 ModelAttributeReferenceSelector(
                                    memberSourceType,
                                    memberType,
                                    parameterContext.modelAttributeTypeReference().toCompilationUnits()
                                 ).right()
                              }
                        } else {
                           CompilationError(
                              parameterContext.start,
                              "Model Attribute References are only allowed within Views"
                           ).asList().left()

                        }
                     }

                     else -> TODO("readFunction parameter accessor not defined for code ${functionContext.source().content}")

                  }.flatMap { parameterAccessor ->
                     typeChecker.ifAssignable(
                        parameterAccessor.returnType, parameterType.basePrimitive
                           ?: PrimitiveType.ANY, parameterContext
                     ) {
                        parameterAccessor
                     }.wrapErrorsInList()
                  }
                  parameterAccessor
               }
                  .invertEitherList()
                  .flattenErrors()
                  .flatMap { parameters ->
                     if (function.modifiers.contains(FunctionModifiers.Query) && !functionContext.isInViewContext()) {
                        CompilationError(
                           functionContext.start,
                           "Query functions may only be used within view definitions"
                        ).asList().left()
                     } else {
                        FunctionAccessor.buildAndResolveTypeArguments(function, parameters).right()
                     }
                  }
            }
         }
   }

   private fun compileTypeReferenceAccessor(
      namespace: String,
      parameterContext: TaxiParser.ParameterContext
   ): Either<List<CompilationError>, TypeReferenceSelector> {
      return tokenProcessor.typeOrError(namespace, parameterContext.typeReferenceSelector().typeType()).map { type ->
         TypeReferenceSelector(type)
      }
   }

   private fun compileFieldReferenceAccessor(
      function: Function,
      parameterContext: TaxiParser.ParameterContext
   ): FieldReferenceSelector {
      val fieldName = parameterContext.fieldReferenceSelector().Identifier().text
      // MP - 30-Sep: This used to be the implementation - where it looks like anonymous types are resolving field lookups differently,
      // resolving against the declaring type - which is inconsistent and wrong.
      // Leaving this here, as making this change will no doubt break something, but I'm not sure what.
      // Hwoever, I strongly discourage returning to this implementation unless there's a really really good reason.
//      val fieldType = if (anonymousTypeResolutionContext.concreteProjectionTypeContext != null || anonymousTypeResolutionContext.typesToDiscover.isNotEmpty()) {
//         val errorsOrFieldType = if (anonymousTypeResolutionContext.concreteProjectionTypeContext != null) {
//            tokenProcessor.typeOrError(parameterContext.findNamespace(), anonymousTypeResolutionContext.concreteProjectionTypeContext)
//         } else {
//            tokenProcessor.getType(parameterContext.findNamespace(),
//            anonymousTypeResolutionContext.typesToDiscover.first().type.firstTypeParameterOrSelf, parameterContext)
//         }
//         errorsOrFieldType.collectErrors(errors).map { it }.getOrElse { PrimitiveType.ANY }
//      } else {
//         provideField(fieldName, parameterContext.fieldReferenceSelector())
//            .collectErrors(errors)
//            .map { it.type }
//            .getOrElse { PrimitiveType.ANY }
//      }
//   }
      val fieldType = provideField(fieldName, parameterContext.fieldReferenceSelector())
         .collectErrors(errors)
         .map { it.type }
         .getOrElse { PrimitiveType.ANY }
      return FieldReferenceSelector(fieldName, fieldType)
   }

   fun typeResolver(namespace: Namespace) = tokenProcessor.typeResolver(namespace)
   fun lookupTypeByName(text: String, contextRule: ParserRuleContext) =
      tokenProcessor.lookupTypeByName(text, contextRule)

   fun lookupTypeByName(typeContext: TaxiParser.TypeTypeContext) = tokenProcessor.lookupTypeByName(typeContext)
   fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext) =
      tokenProcessor.parseType(namespace, typeType)

   fun parseModelAttributeTypeReference(
      namespace: Namespace,
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, Pair<QualifiedName, Type>> =
      tokenProcessor.parseModelAttributeTypeReference(namespace, modelAttributeReferenceCtx)
}
