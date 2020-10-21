package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.log
import org.antlr.v4.runtime.ParserRuleContext

internal class FieldCompiler(private val tokenProcessor: TokenProcessor,
                             private val typeBody: TaxiParser.TypeBodyContext,
                             private val typeName: String,
                             private val errors: MutableList<CompilationError>

) {
   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)
   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)

   private val fieldsBeingCompiled = mutableSetOf<String>()
   private val compiledFields = mutableMapOf<String, Either<List<CompilationError>, Field>>()
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

   fun compileAllFields(): List<Either<List<CompilationError>, Field>> {
      val namespace = typeBody.findNamespace()
      val conditionalFieldStructures = typeBody.conditionalTypeStructureDeclaration()?.map { conditionalFieldBlock ->
         conditionalFieldSetProcessor.compileConditionalFieldStructure(conditionalFieldBlock, namespace)
//            .map { it.fields }
      } ?: emptyList()

      val fieldsWithConditions = conditionalFieldStructures.map {
         it.map { it.fields }
      }
      val calculatedFieldStructures = typeBody.calculatedMemberDeclaration()?.mapNotNull { calculatedMemberDeclarationContext ->
         calculatedFieldSetProcessor.compileCalculatedField(calculatedMemberDeclarationContext, namespace)
      } ?: emptyList()

      val memberDeclarations = typeBody.typeMemberDeclaration() ?: emptyList()
      val fields = memberDeclarations.map { member ->
         provideField(TokenProcessor.unescape(member.fieldDeclaration().Identifier().text), member)
      }
      return fields
   }

   private fun compileField(fieldName: String, requestingToken: ParserRuleContext): Either<List<CompilationError>, Field> {
      val memberDeclaration = typeBody.typeMemberDeclaration()
         .firstOrNull { TokenProcessor.unescape(it.fieldDeclaration().Identifier().text) == fieldName }
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
      return tokenProcessor.parseType(namespace, member.fieldDeclaration().typeType())
         .mapLeft { listOf(it) }
         .flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
   }

   private fun toField(member: TaxiParser.TypeMemberDeclarationContext,
                       namespace: Namespace,
                       type: Type,
                       typeDoc: String?,
                       fieldAnnotations: List<Annotation>): Either<List<CompilationError>, Field> {
      val accessor = compileAccessor(member.fieldDeclaration().accessor(), type)
      return tokenProcessor.mapConstraints(member.fieldDeclaration().typeType(), type, namespace).map { constraints ->
         Field(
            name = TokenProcessor.unescape(member.fieldDeclaration().Identifier().text),
            type = type,
            nullable = member.fieldDeclaration().typeType().optionalType() != null,
            modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
            annotations = fieldAnnotations,
            constraints = constraints,
            accessor = accessor,
            typeDoc = typeDoc
         )
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
      return tokenProcessor.parseType(namespace, formula, member.fieldDeclaration().typeType())
         .mapLeft { listOf(it) }
         .flatMap { type -> toField(member, namespace, type, typeDoc, fieldAnnotations) }
   }

   fun compileAccessor(accessor: TaxiParser.AccessorContext?, targetType: Type): Accessor? {
      return when {
         accessor == null -> null
         accessor.scalarAccessor() != null -> compileScalarAccessor(accessor.scalarAccessor(), targetType)
         accessor.objectAccessor() != null -> compileDestructuredAccessor(accessor.objectAccessor(), targetType)
         else -> null
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
         val accessor = compileAccessor(fieldDeclaration.accessor(), fieldType)
            ?: throw CompilationException(fieldDeclaration.start, "Expected an accessor to be defined", block.source().sourceName)
         fieldName to accessor
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

   internal fun compileScalarAccessor(accessor: TaxiParser.ScalarAccessorContext, targetType: Type?): Accessor {
      return compileScalarAccessor(accessor.scalarAccessorExpression(), targetType)

   }

   internal fun compileScalarAccessor(expression: TaxiParser.ScalarAccessorExpressionContext, targetType: Type?): Accessor {
      if (targetType == null) {
         log().warn("Type not provided, not performing type checks")
      }
      return when {
         expression.jsonPathAccessorDeclaration() != null -> JsonPathAccessor(expression.jsonPathAccessorDeclaration().accessorExpression().text.removeSurrounding("\""))
         expression.xpathAccessorDeclaration() != null -> XpathAccessor(expression.xpathAccessorDeclaration().accessorExpression().text.removeSurrounding("\""))
         expression.columnDefinition() != null -> {
            ColumnAccessor(index =
            expression.columnDefinition().columnIndex().StringLiteral()?.text
               ?: expression.columnDefinition().columnIndex().IntegerLiteral().text.toInt(), defaultValue = null)
         }
         expression.conditionalTypeConditionDeclaration() != null -> {
            val namespace = expression.conditionalTypeConditionDeclaration().findNamespace()
            conditionalFieldSetProcessor.compileCondition(expression.conditionalTypeConditionDeclaration(), namespace)
               .map { condition -> ConditionalAccessor(condition) }
               // TODO : Make the current method return Either<>
               .getOrHandle { throw CompilationException(it) }
         }
         expression.defaultDefinition() != null -> {
            val defaultValue = parseDefaultValue(expression.defaultDefinition(), targetType!!)
               .collectError(errors).getOrElse { null }
            ColumnAccessor(
               index = null,
               defaultValue = defaultValue)
         }

         expression.readFunction() != null -> {
            val functionContext = expression.readFunction()
            buildReadFunctionAccessor(functionContext, targetType!!)
         }
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun buildReadFunctionAccessor(functionContext: TaxiParser.ReadFunctionContext, targetType: Type): FunctionAccessor {
      val namespace = functionContext.findNamespace()
      return tokenProcessor.attemptToLookupTypeByName(namespace, functionContext.functionName().qualifiedName().Identifier().text(), functionContext).flatMap { qualifiedName ->

         tokenProcessor.resolveFunction(qualifiedName, functionContext).map { function ->
            require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
            TypeChecking.assertIsAssignable(function.returnType!!, targetType, functionContext)?.let { compilationError ->
               errors.add(compilationError)
            }
            val parameters = functionContext.formalParameterList().parameter().mapIndexed { parameterIndex, parameterContext ->
               val parameterType = function.getParameterType(parameterIndex)
               val parameterAccessor = when {
                  parameterContext.literal() != null -> LiteralAccessor(parameterContext.literal().value()).right()
                  parameterContext.scalarAccessorExpression() != null -> compileScalarAccessor(parameterContext.scalarAccessorExpression(), parameterType).right()
//                  parameterContext.readFunction() !s= null -> buildReadFunctionAccessor(parameterContext.readFunction()).right()
//                  parameterContext.columnDefinition() != null -> buildColumnAccessor(parameterContext.columnDefinition()).right()
                  parameterContext.fieldReferenceSelector() != null -> compileFieldReferenceAccessor(parameterContext).right()
                  parameterContext.typeReferenceSelector() != null -> compileTypeReferenceAccessor(namespace, parameterContext).right()
                  else -> TODO("readFunction parameter accessor not defined for code ${functionContext.source().content}")
               }.flatMap { parameterAccessor ->
                     TypeChecking.ifAssignable(parameterAccessor.returnType, parameterType.basePrimitive
                        ?: PrimitiveType.ANY, parameterContext) {
                        parameterAccessor
                     }
                  }

               parameterAccessor
            }.reportAndRemoveErrors(errors)
            FunctionAccessor(function, parameters)
         }
      }.getOrHandle { throw CompilationException(it) }
   }

   private fun compileTypeReferenceAccessor(namespace: String, parameterContext: TaxiParser.ParameterContext): TypeReferenceSelector {
      return tokenProcessor.typeOrError(namespace, parameterContext.typeReferenceSelector().typeType()).map { type ->
         TypeReferenceSelector(type)
      }.getOrHandle { throw CompilationException(it) }
   }

   private fun compileFieldReferenceAccessor(parameterContext: TaxiParser.ParameterContext): FieldReferenceSelector {
      val fieldName = parameterContext.fieldReferenceSelector().Identifier().text
      val fieldType = provideField(fieldName, parameterContext.fieldReferenceSelector())
         .collectErrors(errors)
         .map { it.type }
         .getOrElse { PrimitiveType.ANY }
      return FieldReferenceSelector(fieldName, fieldType)
   }

   private fun buildColumnAccessor(columnDefinition: TaxiParser.ColumnDefinitionContext): Accessor {
      val columnIndex = columnDefinition.columnIndex()
      return when {
         columnIndex.IntegerLiteral() != null -> ColumnAccessor(columnIndex.IntegerLiteral().text.toInt(), defaultValue = null)
         columnIndex.StringLiteral() != null -> ColumnAccessor(columnIndex.StringLiteral().text, defaultValue = null)
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

   private fun parseDefaultValue(defaultDefinitionContext: TaxiParser.DefaultDefinitionContext, targetType: Type): Either<CompilationError, Any?> {
      return when {
         defaultDefinitionContext.literal() != null -> {
            assertLiteralDefaultValue(targetType, defaultDefinitionContext.literal().value(), defaultDefinitionContext)
         }
         defaultDefinitionContext.qualifiedName() != null -> {
            if (targetType !is EnumType) {
               CompilationError(defaultDefinitionContext.qualifiedName().start, "Cannot use an enum as a reference here, as ${targetType.qualifiedName} is not an enum").left()
            } else {
               val (enumTypeName, enumValue) = EnumValue.qualifiedNameFrom(defaultDefinitionContext.qualifiedName().text)
               val enumValueQualifiedName = "$enumTypeName.$enumValue"
               if (targetType.qualifiedName != enumTypeName.fullyQualifiedName) {
                  CompilationError(defaultDefinitionContext.qualifiedName().start, "Cannot assign a default of $enumValueQualifiedName to an enum with a type of ${targetType.qualifiedName} because the types are different").left()
               } else {
                  assertEnumDefaultValueCompatibility(targetType, enumValueQualifiedName, defaultDefinitionContext.qualifiedName())
               }
            }
         }
         else -> error("Unexpected branch of parseDefaultValue didn't match any conditions")
      }
   }

   private fun assertTypesCompatible(originalType: Type, refinedType: Type, fieldName: String, typeName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext): Type {
      val refinedUnderlyingType = TypeAlias.underlyingType(refinedType)
      val originalUnderlyingType = TypeAlias.underlyingType(originalType)

      if (originalUnderlyingType != refinedUnderlyingType) {
         throw CompilationException(typeRule.start, "Cannot refine field $fieldName on $typeName to ${refinedType.qualifiedName} as it maps to ${refinedUnderlyingType.qualifiedName} which is incompatible with the existing type of ${originalType.qualifiedName}", typeRule.source().sourceName)
      }
      return refinedType
   }

   private fun assertLiteralDefaultValue(targetType: Type, defaultValue: Any, typeRule: ParserRuleContext): Either<CompilationError, Any> {
      val valid = when {
         targetType.basePrimitive == PrimitiveType.STRING && defaultValue is String -> true
         targetType.basePrimitive == PrimitiveType.DECIMAL && defaultValue is Number -> true
         targetType.basePrimitive == PrimitiveType.INTEGER && defaultValue is Number -> true
         targetType.basePrimitive == PrimitiveType.BOOLEAN && defaultValue is Boolean -> true
         else -> false
      }
      return if (!valid) {
         CompilationError(typeRule.start, "Default value $defaultValue is not compatible with ${targetType.basePrimitive?.qualifiedName}", typeRule.source().sourceName).left()
      } else {
         defaultValue.right()
      }
   }

   private fun assertEnumDefaultValueCompatibility(enumType: EnumType, defaultValue: String, typeRule: ParserRuleContext): Either<CompilationError, EnumValue> {
      return enumType.values.firstOrNull { enumValue -> enumValue.qualifiedName == defaultValue }?.right()
         ?: CompilationError(typeRule.start, "${enumType.toQualifiedName().fullyQualifiedName} has no value of $defaultValue", typeRule.source().sourceName).left()
   }


   fun typeResolver(namespace: Namespace) = tokenProcessor.typeResolver(namespace)
   fun lookupTypeByName(text: String, contextRule: ParserRuleContext) = tokenProcessor.lookupTypeByName(text, contextRule)
   fun lookupTypeByName(typeContext: TaxiParser.TypeTypeContext) = tokenProcessor.lookupTypeByName(typeContext)
   fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext) = tokenProcessor.parseType(namespace, typeType)
}
