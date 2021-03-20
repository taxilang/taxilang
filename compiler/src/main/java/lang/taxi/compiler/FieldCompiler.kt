package lang.taxi.compiler

import arrow.core.Either
import arrow.core.extensions.either.monad.flatMap
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import lang.taxi.CompilationError
import lang.taxi.CompilationException
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionExpressionAccessor
import lang.taxi.source
import lang.taxi.text
import lang.taxi.toCompilationUnit
import lang.taxi.types.Accessor
import lang.taxi.types.Annotation
import lang.taxi.types.ColumnAccessor
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.DestructuredAccessor
import lang.taxi.types.DiscoveryType
import lang.taxi.types.Field
import lang.taxi.types.FieldModifier
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FieldSourceAccessor
import lang.taxi.types.Formula
import lang.taxi.types.FormulaOperator
import lang.taxi.types.JsonPathAccessor
import lang.taxi.types.LiteralAccessor
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.ReadFunction
import lang.taxi.types.ReadFunctionArgument
import lang.taxi.types.ReadFunctionFieldAccessor
import lang.taxi.types.Type
import lang.taxi.types.TypeReferenceSelector
import lang.taxi.types.XpathAccessor
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.value
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Wrapper interface for "Things we need to compile fields from".
 * We compile either for a type body, or an annotation body.
 */
interface TypeWithFieldsContext {
   fun findNamespace(): String
   fun memberDeclaration(fieldName: String, compilingTypeName: String, requestingToken: ParserRuleContext): Either<List<CompilationError>, TaxiParser.TypeMemberDeclarationContext> {
      val memberDeclaration = this.memberDeclarations
         .firstOrNull { TokenProcessor.unescape(it.fieldDeclaration().Identifier().text) == fieldName }

      return memberDeclaration.rightIfNotNull {
         listOf(CompilationError(requestingToken.start, "Field $fieldName does not exist on type $compilingTypeName"))
      }
   }

   val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
   val calculatedMemberDeclarations: List<TaxiParser.CalculatedMemberDeclarationContext>
   val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
}

class AnnotationTypeBodyContent(private val typeBody: TaxiParser.AnnotationTypeBodyContext?, val namespace: String) : TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext> = emptyList()
   override val calculatedMemberDeclarations: List<TaxiParser.CalculatedMemberDeclarationContext> = emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()
}

class TypeBodyContext(private val typeBody: TaxiParser.TypeBodyContext?, val namespace: String) : TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
      get() = typeBody?.conditionalTypeStructureDeclaration() ?: emptyList()
   override val calculatedMemberDeclarations: List<TaxiParser.CalculatedMemberDeclarationContext>
      get() = typeBody?.calculatedMemberDeclaration() ?: emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()

}

internal class FieldCompiler(private val tokenProcessor: TokenProcessor,
                             private val typeBody: TypeWithFieldsContext,
                             private val typeName: String,
                             private val errors: MutableList<CompilationError>,
                             private val anonymousTypeResolutionContext: AnonymousTypeResolutionContext = AnonymousTypeResolutionContext()

) {
   internal val typeChecker = tokenProcessor.typeChecker
   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)
   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)
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
         return listOf(CompilationError(requestingToken.start, "Cyclic dependency detected - field $fieldName is currently being compiled")).left()
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
      val calculatedFieldStructures = typeBody.calculatedMemberDeclarations.map { calculatedMemberDeclarationContext ->
         calculatedFieldSetProcessor.compileCalculatedField(calculatedMemberDeclarationContext, namespace)
      }.invertEitherList().flattenErrors().collectErrors(errors).getOrElse { emptyList() }

      val fields = typeBody.memberDeclarations.map { member ->
         provideField(TokenProcessor.unescape(member.fieldDeclaration().Identifier().text), member)
      }.mapNotNull { either -> either.collectErrors(errors).getOrElse { null } }
      return fields + calculatedFieldStructures + fieldsWithConditions
   }

   private fun compileField(fieldName: String, requestingToken: ParserRuleContext): Either<List<CompilationError>, Field> {
      val memberDeclaration = fieldNamesToDefinitions[fieldName]
         ?: return listOf(CompilationError(requestingToken.start, "Field $fieldName does not exist on type $typeName")).left()

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
      val simpleType = member.fieldDeclaration().simpleFieldDeclaration()?.typeType()
      val anonymousTypeDefinition = member.fieldDeclaration().anonymousTypeDefinition()
      return when {
         simpleType != null -> {
            val fieldType = tokenProcessor.parseType(namespace, simpleType);
            fieldType.flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
         }

         anonymousTypeDefinition != null -> {
            val anonymousTypeBody = member.fieldDeclaration().anonymousTypeDefinition().typeBody()
            val anonymousTypeName = "$typeName$${member.fieldDeclaration().Identifier().text.capitalize()}"
            val fieldType = tokenProcessor.parseAnonymousType(namespace, anonymousTypeBody, anonymousTypeName)
            fieldType.flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
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
               Either.left(listOf(CompilationError(member.start, "The type for $fieldName can not be resolved without a query context")))
            } else {
               val typeOrError = this.tokenProcessor.getType(namespace, discoveryTypes.first().type.firstTypeParameterOrSelf, member)
               typeOrError.flatMap { type ->
                  when {
                     type !is ObjectType -> Either.left(listOf(CompilationError(member.start, "$typeName should be an object type containing field $fieldName")))
                     !type.hasField(fieldName) -> Either.left(listOf(CompilationError(member.start, "$typeName should be an object type containing field $fieldName")))
                     else -> type.field(fieldName).right()
                  }
               }
            }
         }
      }

   }


   private fun toField(member: TaxiParser.TypeMemberDeclarationContext,
                       namespace: Namespace,
                       type: Type,
                       typeDoc: String?,
                       fieldAnnotations: List<Annotation>): Either<List<CompilationError>, Field> {
      return if (member.fieldDeclaration().anonymousTypeDefinition() != null) {
         val accessor = compileAccessor(member.fieldDeclaration().anonymousTypeDefinition().accessor(), type)
            .getOrHandle {
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
            compilationUnit = member.fieldDeclaration().toCompilationUnit()
         ).right()
      } else {
         val accessor = compileAccessor(member.fieldDeclaration().simpleFieldDeclaration().accessor(), type)
            .getOrHandle {
               errors.addAll(it)
               null
            }
         val simpleType = member.fieldDeclaration().simpleFieldDeclaration().typeType()
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
            compilationUnit = member.fieldDeclaration().toCompilationUnit())
         }
      }
   }

   private fun mapFieldModifiers(fieldModifier: TaxiParser.FieldModifierContext?): List<FieldModifier> {
      if (fieldModifier == null) return emptyList()
      val modifier = FieldModifier.values().firstOrNull { it.token == fieldModifier.text }
         ?: error("Unknown field modifier: ${fieldModifier.text}")
      return listOf(modifier)
   }

   internal fun compileCalculatedField(member: TaxiParser.TypeMemberDeclarationContext,
                                       formula: Formula,
                                       namespace: Namespace): Either<List<CompilationError>, Field> {
      val fieldAnnotations = tokenProcessor.collateAnnotations(member.annotation())
      val typeDoc = tokenProcessor.parseTypeDoc(member.typeDoc())
      return tokenProcessor.parseType(namespace, formula, member.fieldDeclaration().simpleFieldDeclaration().typeType())
         .flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
   }

   fun compileAccessor(accessor: TaxiParser.AccessorContext?, targetType: Type): Either<List<CompilationError>, Accessor?> {
      return when {
         accessor == null -> Either.right(null)
         accessor.scalarAccessor() != null -> compileScalarAccessor(accessor.scalarAccessor(), targetType)
         accessor.objectAccessor() != null -> compileDestructuredAccessor(accessor.objectAccessor(), targetType).right()
         else -> Either.right(null)
      }
   }

   private fun compileDestructuredAccessor(block: TaxiParser.ObjectAccessorContext, targetType: Type): DestructuredAccessor? {
      if (targetType !is ObjectType) {
         this.errors.add(CompilationError(block.start, "Destructuring is not permitted here because ${targetType.qualifiedName} is not an object type"))
         return null
      }

      val accessorErrors = mutableListOf<CompilationError>()
      val fields = block.destructuredFieldDeclaration().mapNotNull { fieldDeclaration ->
         val fieldName = fieldDeclaration.Identifier().text
         if (!targetType.hasField(fieldName)) {
            accessorErrors.add(CompilationError(fieldDeclaration.start, "${targetType.qualifiedName} has no field called $fieldName"))
            return@mapNotNull null
         }
         val fieldType = targetType.field(fieldName).type
         val fieldNameToAccessor = compileAccessor(fieldDeclaration.accessor(), fieldType).flatMap { accessor: Accessor? ->
            if (accessor == null) {
               listOf(CompilationError(fieldDeclaration.start, "Expected an accessor to be defined", block.source().sourceName)).left()
            } else {
               (fieldName to accessor).right()
            }
         }.getOrHandle { errors ->
            accessorErrors.addAll(errors)
            null
         }
         fieldNameToAccessor
      }.toMap()
      // TODO : Validate that the object is fully defined..
      // No invalid fields declared
      // No non-null fields omitted
      return if (accessorErrors.isNotEmpty()) {
         this.errors.addAll(accessorErrors)
         null
      } else {
         DestructuredAccessor(fields)
      }
   }

   internal fun compileScalarAccessor(accessor: TaxiParser.ScalarAccessorContext, targetType: Type): Either<List<CompilationError>, Accessor> {
      return compileScalarAccessor(accessor.scalarAccessorExpression(), targetType)

   }

   internal fun compileScalarAccessor(expression: TaxiParser.ScalarAccessorExpressionContext, targetType: Type = PrimitiveType.ANY): Either<List<CompilationError>, Accessor> {
      if (targetType == PrimitiveType.ANY) {
         log().warn("Type was provided as Any, not performing type checks")
      }
      return when {
         expression.jsonPathAccessorDeclaration() != null -> JsonPathAccessor(
            expression = expression.jsonPathAccessorDeclaration().accessorExpression().text.removeSurrounding("\""),
            returnType = targetType
         ).right()
         expression.xpathAccessorDeclaration() != null -> XpathAccessor(expression.xpathAccessorDeclaration().accessorExpression().text.removeSurrounding("\""),
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
            conditionalFieldSetProcessor.compileCondition(expression.conditionalTypeConditionDeclaration(), namespace, targetType)
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
         expression.readExpression() != null -> buildReadFunctionExpressionAccessor(expression.readExpression(), targetType)
         expression.byFieldSourceExpression() != null -> buildReadFieldAccessor(expression.byFieldSourceExpression(), targetType)
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun buildReadFieldAccessor(byFieldSourceExpression: TaxiParser.ByFieldSourceExpressionContext, targetType: Type): Either<List<CompilationError>, Accessor> {
      //as {
      //      traderEmail: UserEmail by (this.traderId)
      //}
      val referencedFieldName =  byFieldSourceExpression.fieldReferenceSelector().Identifier().text
      return if (this.anonymousTypeResolutionContext.concreteProjectionTypeContext != null) {
         val concreteProjectionType = this.tokenProcessor.typeOrError(byFieldSourceExpression.findNamespace(), this.anonymousTypeResolutionContext.concreteProjectionTypeContext)
         createFieldSource(concreteProjectionType, byFieldSourceExpression, referencedFieldName, QualifiedName.from(typeName))
      } else {
         createFieldSource(this.tokenProcessor.getType(
            byFieldSourceExpression.findNamespace(),
            this.anonymousTypeResolutionContext.typesToDiscover.first().type.firstTypeParameterOrSelf,
            byFieldSourceExpression),
            byFieldSourceExpression,
            referencedFieldName)
      }
   }

   private fun createFieldSource(
      sourceType:  Either<List<CompilationError>, Type>,
      byFieldSourceExpression: TaxiParser.ByFieldSourceExpressionContext,
      referencedFieldName: String,
      sourceTypeName: QualifiedName? = null): Either<List<CompilationError>, Accessor> {
      return when (sourceType) {
         is Either.Left -> return sourceType.a.left()
         is Either.Right -> {
            if (sourceType.b !is ObjectType) {
               listOf(CompilationError(byFieldSourceExpression.start, "${sourceType.b.qualifiedName} must be an ObjectType", byFieldSourceExpression.source().sourceName)).left()
            }

            val objectType = sourceType.b as ObjectType
            if (!objectType.hasField(referencedFieldName)) {
               listOf(CompilationError(byFieldSourceExpression.start, "${sourceType.b.qualifiedName} should have a field called $referencedFieldName", byFieldSourceExpression.source().sourceName)).left()
            }

            FieldSourceAccessor(
               sourceAttributeName = referencedFieldName,
               attributeType = objectType.field(referencedFieldName).type.toQualifiedName(),
               sourceType = sourceTypeName ?: objectType.toQualifiedName()).right()
         }
      }
   }

   private fun buildReadFunctionExpressionAccessor(readExpressionContext: TaxiParser.ReadExpressionContext, targetType: Type): Either<List<CompilationError>, FunctionExpressionAccessor> {
      val allowedFunctionReturnTypes = setOf(PrimitiveType.INTEGER, PrimitiveType.STRING)
      val allowedOperationTypes = mapOf(
         PrimitiveType.INTEGER to setOf(FormulaOperator.Add, FormulaOperator.Subtract, FormulaOperator.Multiply),
         PrimitiveType.STRING to setOf(FormulaOperator.Add)
      )
      val allowedOperandTypes = mapOf<PrimitiveType, ((value: Any) -> Boolean)>(
         PrimitiveType.INTEGER to { value -> value is Int },
         PrimitiveType.STRING to { value -> value is String })

      return buildReadFunctionAccessor(readExpressionContext.readFunction(), targetType).flatMap { readFunctionAccessor ->
         val functionBaseReturnType = readFunctionAccessor.function.returnType?.basePrimitive
         if (!allowedFunctionReturnTypes.contains(functionBaseReturnType)) {
            throw CompilationException(CompilationError(
               readExpressionContext.start,
               "function needs to return one of these types => ${allowedFunctionReturnTypes.map { it.qualifiedName }.joinToString()}",
               readExpressionContext.source().sourceName))
         }
         val arithmeticOperator = FormulaOperator.forSymbol(readExpressionContext.arithmaticOperator().text)
         if (!allowedOperationTypes[functionBaseReturnType]!!.contains(arithmeticOperator)) {
            throw CompilationException(CompilationError(
               readExpressionContext.start,
               "only the following operations are allowed => ${allowedOperationTypes[functionBaseReturnType]!!.map { it.symbol }.joinToString()}",
               readExpressionContext.source().sourceName))
         }
         val operand = readExpressionContext.literal().value()
         if (!allowedOperandTypes[functionBaseReturnType]!!(operand)) {
            CompilationError(
               readExpressionContext.start,
               "$operand is not an allowed value for ${functionBaseReturnType?.qualifiedName}"
            ).asList().left()
         } else {
            FunctionExpressionAccessor(
               readFunctionAccessor,
               FormulaOperator.forSymbol(readExpressionContext.arithmaticOperator().text),
               readExpressionContext.literal().value()).right()
         }

      }


   }

   private fun buildReadFunctionAccessor(functionContext: TaxiParser.ReadFunctionContext, targetType: Type): Either<List<CompilationError>, FunctionAccessor> {
      val namespace = functionContext.findNamespace()
      return tokenProcessor.attemptToLookupTypeByName(namespace, functionContext.functionName().qualifiedName().Identifier().text(), functionContext, symbolKind = SymbolKind.FUNCTION)
         .wrapErrorsInList()
         .flatMap { qualifiedName ->

            tokenProcessor.resolveFunction(qualifiedName, functionContext).map { function ->
               require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
               typeChecker.assertIsAssignable(function.returnType!!, targetType, functionContext)?.let { compilationError ->
                  errors.add(compilationError)
               }
               val parameters = functionContext.formalParameterList().parameter().mapIndexed { parameterIndex, parameterContext ->
                  val parameterType = function.getParameterType(parameterIndex)
                  val parameterAccessor = when {
                     parameterContext.literal() != null -> LiteralAccessor(parameterContext.literal().value()).right()
                     parameterContext.scalarAccessorExpression() != null -> compileScalarAccessor(parameterContext.scalarAccessorExpression(), parameterType)
//                  parameterContext.readFunction() !s= null -> buildReadFunctionAccessor(parameterContext.readFunction()).right()
//                  parameterContext.columnDefinition() != null -> buildColumnAccessor(parameterContext.columnDefinition()).right()
                     parameterContext.fieldReferenceSelector() != null -> compileFieldReferenceAccessor(parameterContext).right()
                     parameterContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(namespace, parameterContext)
                     else -> TODO("readFunction parameter accessor not defined for code ${functionContext.source().content}")
                  }.flatMap { parameterAccessor ->
                     typeChecker.ifAssignable(parameterAccessor.returnType, parameterType.basePrimitive
                        ?: PrimitiveType.ANY, parameterContext) {
                        parameterAccessor
                     }.wrapErrorsInList()
                  }

                  parameterAccessor
               }.reportAndRemoveErrorList(errors)
               FunctionAccessor(function, parameters)
            }
         }
   }

   private fun compileTypeReferenceAccessor(namespace: String, parameterContext: TaxiParser.ParameterContext): Either<List<CompilationError>, TypeReferenceSelector> {
      return tokenProcessor.typeOrError(namespace, parameterContext.typeReferenceSelector().typeType()).map { type ->
         TypeReferenceSelector(type)
      }
   }

   private fun compileFieldReferenceAccessor(parameterContext: TaxiParser.ParameterContext): FieldReferenceSelector {
      val fieldName = parameterContext.fieldReferenceSelector().Identifier().text
      val fieldType = provideField(fieldName, parameterContext.fieldReferenceSelector())
         .collectErrors(errors)
         .map { it.type }
         .getOrElse { PrimitiveType.ANY }
      return FieldReferenceSelector(fieldName, fieldType)
   }

   private fun buildColumnAccessor(columnDefinition: TaxiParser.ColumnDefinitionContext, targetType: Type): Accessor {
      val columnIndex = columnDefinition.columnIndex()
      return when {
         columnIndex.IntegerLiteral() != null -> ColumnAccessor(columnIndex.IntegerLiteral().text.toInt(), defaultValue = null, returnType = targetType)
         columnIndex.StringLiteral() != null -> ColumnAccessor(columnIndex.StringLiteral().text, defaultValue = null, returnType = targetType)
         else -> error("Unhandled buildColumnAccessor() scenario")
      }
   }

   // This is to provide backwards compatability to hard-coded functions.
   // We need to move to declared functions, with a form of stdlib as well
   private fun processLegacyReadFunction(readFunction: ReadFunction, expression: TaxiParser.ReadFunctionContext): ReadFunctionFieldAccessor {
      val parameters = expression.formalParameterList().parameter().map { parameterContext ->
         when {
            parameterContext.literal() != null -> ReadFunctionArgument(value = parameterContext.literal().value(), columnAccessor = null)
//            parameterContext.scalarAccessorExpression() !=
//            parameterContext.columnDefinition() != null -> ReadFunctionArgument(value = null,
//               columnAccessor = ColumnAccessor(index =
//               parameterContext.columnDefinition().columnIndex().StringLiteral()?.text
//                  ?: parameterContext.columnDefinition().columnIndex().IntegerLiteral().text.toInt(), defaultValue = null)
//            )
            else -> throw CompilationException(CompilationError(expression.start, "invalid parameter", expression.source().normalizedSourceName))
         }
      }
      return ReadFunctionFieldAccessor(readFunction = readFunction, arguments = parameters)

   }

   fun typeResolver(namespace: Namespace) = tokenProcessor.typeResolver(namespace)
   fun lookupTypeByName(text: String, contextRule: ParserRuleContext) = tokenProcessor.lookupTypeByName(text, contextRule)
   fun lookupTypeByName(typeContext: TaxiParser.TypeTypeContext) = tokenProcessor.lookupTypeByName(typeContext)
   fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext) = tokenProcessor.parseType(namespace, typeType)
}
