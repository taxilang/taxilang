package lang.taxi.compiler

import arrow.core.Either
import arrow.core.extensions.either.monad.flatMap
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.orNull
import arrow.core.right
import com.google.common.hash.Hashing
import lang.taxi.*
import lang.taxi.Namespace
import lang.taxi.compiler.CalculatedFieldSetProcessor.Companion.validate
import lang.taxi.parameters
import lang.taxi.policies.CaseCondition
import lang.taxi.policies.Condition
import lang.taxi.policies.ElseCondition
import lang.taxi.policies.Instruction
import lang.taxi.policies.Instructions
import lang.taxi.policies.OperationScope
import lang.taxi.policies.Policy
import lang.taxi.policies.PolicyScope
import lang.taxi.policies.PolicyStatement
import lang.taxi.policies.RuleSet
import lang.taxi.policies.Subjects
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintValidator
import lang.taxi.services.operations.constraints.OperationConstraintConverter
import lang.taxi.toCompilationError
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionDefinition
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.errorOrNull
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.charset.Charset

internal class TokenProcessor(val tokens: Tokens, importSources: List<TaxiDocument> = emptyList(), collectImports: Boolean = true) {

   constructor(tokens: Tokens, collectImports: Boolean) : this(tokens, emptyList(), collectImports)

   private var createEmptyTypesPerformed: Boolean = false
   private val typeSystem: TypeSystem
   private val synonymRegistry: SynonymRegistry<ParserRuleContext>
   private val services = mutableListOf<Service>()
   private val policies = mutableListOf<Policy>()
   private val functions = mutableListOf<Function>()
   private val constraintValidator = ConstraintValidator()

   private val errors = mutableListOf<CompilationError>()

   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)
   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)

   private val tokensCurrentlyCompiling = mutableSetOf<String>()

   init {
      val importedTypes = if (collectImports) {
         val (errorsInImports, types) = ImportedTypeCollator(tokens, importSources).collect()
         this.errors.addAll(errorsInImports)
         types
      } else {
         emptyList()
      }

      typeSystem = TypeSystem(importedTypes)
      synonymRegistry = SynonymRegistry(typeSystem)
   }

   fun buildTaxiDocument(): Pair<List<CompilationError>, TaxiDocument> {
      compile()
      // TODO: Unsure if including the imported types here is a good iddea or not.
      val types = typeSystem.typeList(includeImportedTypes = true).toSet()
      return errors to TaxiDocument(types, services.toSet(), policies.toSet(), functions.toSet())
   }

   // Primarily for language server tooling, rather than
   // compile time - though it should be safe to use in all scnearios
   fun lookupTypeByName(contextRule: TaxiParser.TypeTypeContext): String {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return lookupTypeByName(namespace, contextRule)
   }

   fun lookupTypeByName(text: String, contextRule: ParserRuleContext): String {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return attemptToLookupTypeByName(namespace, text, contextRule).getOrHandle {
         throw CompilationException(it)
      }
   }

   fun findDeclaredTypeNames(): List<QualifiedName> {
      createEmptyTypes()

      // We need to check all the ObjectTypes, to see if they declare any inline type aliases
      val inlineTypeAliases = tokens.unparsedTypes.filter { (_, tokenPair) ->
         val (_, ctx) = tokenPair
         ctx is TaxiParser.TypeDeclarationContext
      }.flatMap { (_, tokenPair) ->
         val (namespace, ctx) = tokenPair
         val typeCtx = ctx as TaxiParser.TypeDeclarationContext
         val typeAliasNames = typeCtx.typeBody()?.typeMemberDeclaration()
            ?.filter { it.exception == null }
            ?.mapNotNull { memberDeclaration ->
               val fieldDeclaration = memberDeclaration.fieldDeclaration()
               if (fieldDeclaration.typeType() != null && fieldDeclaration.typeType().aliasedType() != null) {
                  // This is an inline type alias
                  lookupTypeByName(namespace, memberDeclaration.fieldDeclaration().typeType())
               } else {
                  null
               }
            } ?: emptyList()
         typeAliasNames.map { QualifiedName.from(it) }
      }

      val declaredTypeNames = typeSystem.typeList().map { it.toQualifiedName() }
      return declaredTypeNames + inlineTypeAliases
   }


   private fun lookupTypeByName(namespace: Namespace, type: TaxiParser.TypeTypeContext): String {
      return if (type.primitiveType() != null) {
         PrimitiveType.fromDeclaration(type.primitiveType()!!.text).qualifiedName
      } else {
         lookupTypeByName(namespace, type.classOrInterfaceType().text, importsInSource(type))
      }
   }


   private fun attemptToLookupTypeByName(namespace: Namespace, name: String, context: ParserRuleContext): Either<CompilationError, String> {
      return try {
         Either.right(lookupTypeByName(namespace, name, importsInSource(context)))
      } catch (e: AmbiguousNameException) {
         Either.left(CompilationError(context.start, e.message!!, context.source().normalizedSourceName))
      }
   }

   // THe whole additionalImports thing is for when we're
   // accessing prior to compiling (ie., in the language server).
   // During normal compilation, don't need to pass anything
   @Deprecated("call attemptToQualify, so errors are caught property")
   private fun lookupTypeByName(namespace: Namespace, name: String, importsInSource: List<QualifiedName>): String {
      return typeSystem.qualify(namespace, name, importsInSource)

   }

   private fun compile() {
      createEmptyTypes()
      compileTokens()
      compileTypeExtensions()
      compileServices()
      compilePolicies()

      compileFunctions()
      applySynonymsToEnums()

      // Some validations can't be performed at the time, because
      // they rely on a fully parsed document structure
      validateConstraints()
      validateFormulas()
   }

   private fun applySynonymsToEnums() {
      // Now we have a full picture of all the enums, we can
      // map the synonyms effectively
      val typesWithSynonyms = synonymRegistry.getTypesWithSynonymsRegistered()
      typeSystem.getTokens(includeImportedTypes = true) { typesWithSynonyms.contains(it.toQualifiedName()) }
         .filterIsInstance<EnumType>()
         .map { enum ->
            val valueExtensions = typesWithSynonyms.getValue(enum.toQualifiedName()).flatMap { enumValueQualifiedName ->
               val (_, enumValueName) = Enums.splitEnumValueQualifiedName(enumValueQualifiedName)
               val valueExtensions = synonymRegistry.synonymsFor(enumValueQualifiedName)
                  .distinctBy { it.first }
                  .filter { (_, parserContext) -> registerErrorsForInvalidSynonyms(enum, enumValueName, parserContext) }
                  .filter { (synonymEnumValue, _) ->
                     synonymEnumValue != enumValueQualifiedName &&// Don't allow synonyms to ourselves
                        enum.value(enumValueName).synonyms.none { it == synonymEnumValue } // Ignore synonyms that are already present
                  }
                  .map { (synonym, context) ->
                     EnumValueExtension(enumValueName, emptyList(), listOf(synonym), compilationUnit = context.toCompilationUnit())
                  }
               valueExtensions
            }
            if (valueExtensions.isNotEmpty()) {
               // Bit of a hack here on the compilationUnit.  Not sure what to use
               valueExtensions
                  .forEach {
                     enum.addExtension(EnumExtension(listOf(it), compilationUnit = it.compilationUnit))
                  }
            }
         }

   }

   private fun registerErrorsForInvalidSynonyms(enum: EnumType, enumValueName: String, parserContext: ParserRuleContext): Boolean {
      return if (!enum.hasName(enumValueName)) {
         errors.add(CompilationError(parserContext.start, "$enumValueName is not defined on type ${enum.qualifiedName}"))
         false
      } else {
         true
      }
   }

   fun findDefinition(qualifiedName: String): ParserRuleContext? {
      createEmptyTypes()
      val definitions = this.tokens.unparsedTypes.filter { it.key == qualifiedName }
      return when {
         definitions.isEmpty() -> null
         definitions.size == 1 -> definitions.values.first().second
         else -> {
            error("Found multiple definitions for $qualifiedName - this shouldn't happen")
         }
      }
   }

   private fun validateConstraints() {
      errors.addAll(constraintValidator.validateAll(typeSystem, services))
   }

   private fun validateFormulas() {
      errors.addAll(typeSystem.typeList().filterIsInstance<ObjectType>()
         .flatMap { type ->
            type
               .allFields
               .filter { it.formula != null }
               .flatMap { field ->
                  validate(field, typeSystem, type)
               }
         }
      )
   }


   private fun createEmptyTypes() {
      if (createEmptyTypesPerformed) {
         return
      }
      tokens.unparsedFunctions.forEach { tokenName, (_, token) ->
         typeSystem.register(Function.undefined(tokenName))
      }
      tokens.unparsedTypes.forEach { tokenName, (_, token) ->
         when (token) {
            is TaxiParser.EnumDeclarationContext -> typeSystem.register(EnumType.undefined(tokenName))
            is TaxiParser.TypeDeclarationContext -> typeSystem.register(ObjectType.undefined(tokenName))
            is TaxiParser.TypeAliasDeclarationContext -> typeSystem.register(TypeAlias.undefined(tokenName))
         }
      }
      createEmptyTypesPerformed = true
   }

   private fun compileTokens() {
      val enumUnparsedTypes = tokens
         .unparsedTypes
         .filter { it.value.second is TaxiParser.EnumDeclarationContext }

      val nonEnumParsedTypes = tokens
         .unparsedTypes
         .filter { it.value.second !is TaxiParser.EnumDeclarationContext }

      enumUnparsedTypes.plus(nonEnumParsedTypes).forEach { (tokenName, _) ->
         compileToken(tokenName)
      }
   }

   private fun compileToken(tokenName: String) {
      val (namespace, tokenRule) = tokens.unparsedTypes[tokenName]!!
      if (typeSystem.isDefined(tokenName) && typeSystem.getType(tokenName) is TypeAlias) {
         // As type aliases can be defined inline, it's perfectly acceptable for
         // this to already exist
         return
      }

      if (tokensCurrentlyCompiling.contains(tokenName)) {
         return
      }

      tokensCurrentlyCompiling.add(tokenName)
      try {
         when (tokenRule) {
            is TaxiParser.TypeDeclarationContext -> compileType(namespace, tokenName, tokenRule)
            is TaxiParser.EnumDeclarationContext -> compileEnum(namespace, tokenName, tokenRule).collectErrors()
            is TaxiParser.TypeAliasDeclarationContext -> compileTypeAlias(namespace, tokenName, tokenRule).collectError()
            // TODO : This is a bit broad - assuming that all typeType's that hit this
            // line will be a TypeAlias inline.  It could be a normal field declaration.
            is TaxiParser.TypeTypeContext -> compileInlineTypeAlias(namespace, tokenRule).collectError()
            else -> TODO("Not handled: $tokenRule")
         }
      } catch (exception:Exception) {
         tokensCurrentlyCompiling.remove(tokenName)
         throw  exception
      }

   }

   private fun compileTypeExtensions() {
      val errors = tokens.unparsedExtensions.mapNotNull { (namespace, typeRule) ->
         when (typeRule) {
            is TaxiParser.TypeExtensionDeclarationContext -> compileTypeExtension(namespace, typeRule)
            is TaxiParser.TypeAliasExtensionDeclarationContext -> compileTypeAliasExtension(namespace, typeRule)
            is TaxiParser.EnumExtensionDeclarationContext -> compileEnumExtension(namespace, typeRule)
            else -> TODO("Not handled: $typeRule")
         }
      }
      if (errors.isNotEmpty()) {
         throw CompilationException(errors)
      }
   }

   private fun compileTypeAliasExtension(namespace: Namespace, typeRule: TaxiParser.TypeAliasExtensionDeclarationContext): CompilationError? {
      return attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule).flatMap { typeName ->
         val type = typeSystem.getType(typeName) as TypeAlias
         val annotations = collateAnnotations(typeRule.annotation())
         val typeDoc = parseTypeDoc(typeRule.typeDoc())
         type.addExtension(TypeAliasExtension(annotations, typeRule.toCompilationUnit(), typeDoc))
            .mapLeft { it.toCompilationError(typeRule.start) }
      }.errorOrNull()
   }

   private fun compileTypeExtension(namespace: Namespace, typeRule: TaxiParser.TypeExtensionDeclarationContext): CompilationError? {
      val typeName = when (val typeNameEither = attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule)) {
         is Either.Left -> return typeNameEither.a // return the compilation error now and stop
         is Either.Right -> typeNameEither.b
      }
      val type = typeSystem.getType(typeName) as ObjectType
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc()?.source()?.content)
      val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
         val fieldName = member.typeExtensionFieldDeclaration().Identifier().text
         val fieldAnnotations = collateAnnotations(member.annotation())
         val refinedType = member.typeExtensionFieldDeclaration()?.typeExtensionFieldTypeRefinement()?.typeType()?.let {
            val refinedType = typeSystem.getType(lookupTypeByName(namespace, it.text, importsInSource(it)))
            assertTypesCompatible(type.field(fieldName).type, refinedType, fieldName, typeName, typeRule)
         }

         val enumConstantValue = member
            ?.typeExtensionFieldDeclaration()
            ?.typeExtensionFieldTypeRefinement()
            ?.constantDeclaration()
            ?.qualifiedName()?.let { enumDefaultValue ->
               assertEnumDefaultValueCompatibility(refinedType!! as EnumType, enumDefaultValue.text, fieldName, typeRule)
            }

         val constantValue = enumConstantValue ?: member
            ?.typeExtensionFieldDeclaration()
            ?.typeExtensionFieldTypeRefinement()
            ?.constantDeclaration()
            ?.literal()?.let { literal ->
               val literalValue = literal.value()
               assertLiteralDefaultValue(refinedType!!, literalValue, fieldName, typeRule)
               literalValue
            }

         FieldExtension(fieldName, fieldAnnotations, refinedType, constantValue)
      }
      val errorMessage = type.addExtension(ObjectTypeExtension(annotations, fieldExtensions, typeDoc, typeRule.toCompilationUnit()))
      return errorMessage
         .mapLeft { it.toCompilationError(typeRule.start) }
         .errorOrNull()
   }

   private fun assertTypesCompatible(originalType: Type, refinedType: Type, fieldName: String, typeName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext): Type {
      val refinedUnderlyingType = TypeAlias.underlyingType(refinedType)
      val originalUnderlyingType = TypeAlias.underlyingType(originalType)

      if (originalUnderlyingType != refinedUnderlyingType) {
         throw CompilationException(typeRule.start, "Cannot refine field $fieldName on $typeName to ${refinedType.qualifiedName} as it maps to ${refinedUnderlyingType.qualifiedName} which is incompatible with the existing type of ${originalType.qualifiedName}", typeRule.source().sourceName)
      }
      return refinedType
   }

   private fun assertLiteralDefaultValue(refinedType: Type, defaultValue: Any, fieldName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext) {
      val valid = when {
         refinedType.basePrimitive == PrimitiveType.STRING && defaultValue is String -> true
         refinedType.basePrimitive == PrimitiveType.DECIMAL && defaultValue is Number -> true
         refinedType.basePrimitive == PrimitiveType.INTEGER && defaultValue is Number -> true
         refinedType.basePrimitive == PrimitiveType.BOOLEAN && defaultValue is Boolean -> true
         else -> false
      }
      if (!valid) {
         throw CompilationException(typeRule.start, "Cannot set default value for field $fieldName as $defaultValue as it is not compatible with ${refinedType.basePrimitive?.qualifiedName}", typeRule.source().sourceName)
      }
   }

   private fun assertEnumDefaultValueCompatibility(enumType: EnumType, defaultValue: String, fieldName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext): EnumValue {
      return enumType.values.firstOrNull { enumValue -> enumValue.qualifiedName == defaultValue }
         ?: throw CompilationException(typeRule.start, "Cannot set default value for field $fieldName as $defaultValue as enum ${enumType.toQualifiedName().fullyQualifiedName} does not have corresponding value", typeRule.source().sourceName)
   }

   private fun compileTypeAlias(namespace: Namespace, tokenName: String, tokenRule: TaxiParser.TypeAliasDeclarationContext): Either<CompilationError, TypeAlias> {
      return parseType(namespace, tokenRule.aliasedType().typeType()).map { aliasedType ->
         val annotations = collateAnnotations(tokenRule.annotation())
         val definition = TypeAliasDefinition(aliasedType, annotations, tokenRule.toCompilationUnit(), typeDoc = parseTypeDoc(tokenRule.typeDoc()))
         val typeAlias = TypeAlias(tokenName, definition)
         this.typeSystem.register(typeAlias)
         typeAlias
      }
   }

   private fun <T> Either<List<CompilationError>, T>.collectErrors(): Either<List<ReportedError>, T> {
      return this.mapLeft { errors ->
         errors.map { error ->
            this@TokenProcessor.errors.add(error)
            ReportedError(error)
         }
      }
   }

   private fun <T> Either<CompilationError, T>.collectError(): Either<ReportedError, T> {
      return this.mapLeft { error ->
         this@TokenProcessor.errors.add(error)
         ReportedError(error)
      }
   }

   private fun <T : Any> List<Either<List<CompilationError>, T>>.reportAndRemoveErrorList(): List<T> {
      return this.mapNotNull { item ->
         item.getOrHandle { errors ->
            this@TokenProcessor.errors.addAll(errors)
            null
         }
      }
   }

   private fun <T : Any> List<Either<CompilationError, T>>.reportAndRemoveErrors(): List<T> {
      return this.mapNotNull { it.reportIfCompilationError() }
   }

   private fun <T : Any> Either<CompilationError, T>.reportIfCompilationError(): T? {
      return this.getOrHandle { compilationError ->
         this@TokenProcessor.errors.add(compilationError)
         null
      }
   }

   // Wrapper class to indicate that an underlying error has been captured, but handled
   // This is primarily to stop us processing errors multiple times as they make their way
   // up the stack
   data class ReportedError(val error: CompilationError)

   fun List<TerminalNode>.text(): String {
      return this.joinToString(".")
   }


   private fun compileType(namespace: Namespace, typeName: String, ctx: TaxiParser.TypeDeclarationContext) {
      val fields = ctx.typeBody()?.let { typeBody ->
         val conditionalFieldStructures = typeBody.conditionalTypeStructureDeclaration()?.map { conditionalFieldBlock ->
            conditionalFieldSetProcessor.compileConditionalFieldStructure(conditionalFieldBlock, namespace)
         }?.reportAndRemoveErrors() ?: emptyList()

         val calculatedFieldStructures = typeBody.calculatedMemberDeclaration()?.mapNotNull { calculatedMemberDeclarationContext ->
            calculatedFieldSetProcessor.compileCalculatedField(calculatedMemberDeclarationContext, namespace)
         }?.reportAndRemoveErrorList() ?: emptyList()

         val fieldsWithConditions = conditionalFieldStructures.flatMap { it.fields }
         val fields = (typeBody.typeMemberDeclaration()?.map { member ->
            val compiledField = compileField(member, namespace)
            compiledField
         } ?: emptyList()).reportAndRemoveErrorList() + fieldsWithConditions + calculatedFieldStructures

         fields
      } ?: emptyList()

      val annotations = collateAnnotations(ctx.annotation())
      val modifiers = parseModifiers(ctx.typeModifier())
      val inherits = parseTypeInheritance(namespace, ctx.listOfInheritedTypes())
      val typeDoc = parseTypeDoc(ctx.typeDoc()?.source()?.content)
      val format: List<String>? = null
      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(
         fields = fields.toSet(),
         annotations = annotations.toSet(),
         modifiers = modifiers,
         inheritsFrom = inherits,
         format = format,
         typeDoc = typeDoc,
         compilationUnit = ctx.toCompilationUnit()
      )))
   }

   internal fun compiledField(member: TaxiParser.TypeMemberDeclarationContext, namespace: Namespace): Field? {
      return compileField(member, namespace)
         .collectErrors()
         .orNull()
   }

   private fun compileField(member: TaxiParser.TypeMemberDeclarationContext, namespace: Namespace): Either<List<CompilationError>, Field> {
      val fieldAnnotations = collateAnnotations(member.annotation())
      val accessor = compileAccessor(member.fieldDeclaration().accessor())
      val typeDoc = parseTypeDoc(member.typeDoc())
      return parseType(namespace, member.fieldDeclaration().typeType())
         .mapLeft { listOf(it) }
         .flatMap { type -> toField(member, namespace, type, accessor, typeDoc, fieldAnnotations) }
   }

   internal fun compileCalculatedField(member: TaxiParser.TypeMemberDeclarationContext,
                                       formula: Formula,
                                       namespace: Namespace): Either<List<CompilationError>, Field> {
      val fieldAnnotations = collateAnnotations(member.annotation())
      val accessor = compileAccessor(member.fieldDeclaration().accessor())
      val typeDoc = parseTypeDoc(member.typeDoc())
      return parseType(namespace, formula, member.fieldDeclaration().typeType())
         .mapLeft { listOf(it) }
         .flatMap { type -> toField(member, namespace, type, accessor, typeDoc, fieldAnnotations) }
   }

   private fun toField(member: TaxiParser.TypeMemberDeclarationContext,
                       namespace: Namespace,
                       type: Type,
                       accessor: Accessor?,
                       typeDoc: String?,
                       fieldAnnotations: List<Annotation>): Either<List<CompilationError>, Field> {
      return mapConstraints(member.fieldDeclaration().typeType(), type, namespace).map { constraints ->
         Field(
            name = unescape(member.fieldDeclaration().Identifier().text),
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

   private fun parseTypeDoc(content: String?): String? {
      if (content == null) {
         return null
      }

      return content.removeSurrounding("[[", "]]").trimIndent().trim()
   }

   private fun parseColumnName(content: String): String {
      return content.trim('"')
   }

   private fun compileAccessor(accessor: TaxiParser.AccessorContext?): Accessor? {
      return when {
         accessor == null -> null
         accessor.scalarAccessor() != null -> compileScalarAccessor(accessor.scalarAccessor())
         accessor.objectAccessor() != null -> compileDestructuredAccessor(accessor.objectAccessor())
         else -> null
      }
   }

   private fun compileDestructuredAccessor(block: TaxiParser.ObjectAccessorContext): DestructuredAccessor {
      val fields = block.destructuredFieldDeclaration().map { fieldDeclaration ->
         val fieldName = fieldDeclaration.Identifier().text
         val accessor = compileAccessor(fieldDeclaration.accessor())
            ?: throw CompilationException(fieldDeclaration.start, "Expected an accessor to be defined", block.source().sourceName)
         fieldName to accessor
      }.toMap()
      // TODO : Validate that the object is fully defined..
      // No invalid fields declared
      // No non-null fields omitted
      return DestructuredAccessor(fields)
   }

   internal fun compileScalarAccessor(accessor: TaxiParser.ScalarAccessorContext): Accessor {
      return compileScalarAccessor(accessor.scalarAccessorExpression())

   }

   internal fun compileScalarAccessor(expression: TaxiParser.ScalarAccessorExpressionContext): Accessor {
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
            ColumnAccessor(index = null, defaultValue = expression.defaultDefinition().literal().value())
         }

         expression.readFunction() != null -> {
            val functionContext = expression.readFunction()
            buildReadFunctionAccessor(functionContext)
         }
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun buildReadFunctionAccessor(functionContext: TaxiParser.ReadFunctionContext): FunctionAccessor {
      val namespace = functionContext.findNamespace()
      return attemptToLookupTypeByName(namespace, functionContext.functionName().qualifiedName().Identifier().text(), functionContext).flatMap { qualifiedName ->

         resolveFunction(qualifiedName, functionContext).map { function ->
            require(function.isDefined) { "Function should have already been compiled before evaluation in a read function expression" }
            val parameters = functionContext.formalParameterList().parameter().map { parameterContext ->
               when {
                  parameterContext.literal() != null -> LiteralAccessor(parameterContext.literal().value())
                  parameterContext.scalarAccessorExpression() != null -> compileScalarAccessor(parameterContext.scalarAccessorExpression())
//                  parameterContext.readFunction() != null -> buildReadFunctionAccessor(parameterContext.readFunction())
//                  parameterContext.columnDefinition() != null -> buildColumnAccessor(parameterContext.columnDefinition())
                  parameterContext.fieldReferenceSelector() != null -> FieldReferenceSelector(parameterContext.fieldReferenceSelector().Identifier().text)
                  parameterContext.typeReferenceSelector() != null -> {
                     typeOrError(namespace, parameterContext.typeReferenceSelector().typeType()).map { type ->
                        TypeReferenceSelector(type)
                     }.getOrHandle { throw CompilationException(it) }
                  }
                  else -> TODO("readFunction parameter accessor not defined for code ${functionContext.source().content}")
               }
            }
            FunctionAccessor(function, parameters)
         }
      }.getOrHandle { throw CompilationException(it) }
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

   private fun mapFieldModifiers(fieldModifier: TaxiParser.FieldModifierContext?): List<FieldModifier> {
      if (fieldModifier == null) return emptyList()
      val modifier = FieldModifier.values().firstOrNull { it.token == fieldModifier.text }
         ?: error("Unknown field modifier: ${fieldModifier.text}")
      return listOf(modifier)
   }

   private fun unescape(text: String): String {
      return text.removeSurrounding("`")
   }

   private fun parseTypeInheritance(namespace: Namespace, listOfInheritedTypes: TaxiParser.ListOfInheritedTypesContext?): Set<Type> {
      if (listOfInheritedTypes == null) return emptySet()
      return listOfInheritedTypes.typeType().mapNotNull { typeTypeContext ->

         parseInheritedType(namespace, typeTypeContext) {
            when (it) {
               is EnumType -> Either.left(CompilationError(typeTypeContext.start, "A Type cannot inherit from an Enum"))
               else -> Either.right(it)
            }
         }

      }.toSet()
   }

   private fun parseEnumInheritance(namespace: Namespace, enumInheritedTypeContext: TaxiParser.EnumInheritedTypeContext?): Type? {
      if (enumInheritedTypeContext == null) return null

      val typeTypeContext = enumInheritedTypeContext.typeType()
      return parseInheritedType(namespace, typeTypeContext) {
         when (it) {
            !is EnumType -> Either.left(CompilationError(typeTypeContext.start, "An Enum can only inherit from an Enum"))
            else -> Either.right(it)
         }
      }

   }

   private inline fun parseInheritedType(namespace: Namespace, typeTypeContext: TaxiParser.TypeTypeContext, filter: (Type) -> Either<CompilationError, Type>): Type? {
      val inheritedTypeOrError = parseType(namespace, typeTypeContext)

      val inheritedEnumTypeOrError = if (inheritedTypeOrError.isRight()) {
         filter(inheritedTypeOrError.getOrElse { null }!!)
      } else inheritedTypeOrError

      return inheritedEnumTypeOrError.reportIfCompilationError()
   }


   private fun parseModifiers(typeModifier: MutableList<TaxiParser.TypeModifierContext>): List<Modifier> {
      return typeModifier.map { Modifier.fromToken(it.text) }
   }

   private fun collateAnnotations(annotations: List<TaxiParser.AnnotationContext>): List<Annotation> {
      return annotations.map { annotation ->
         val params: Map<String, Any> = mapAnnotationParams(annotation)
         val name = annotation.qualifiedName().text
         Annotation(name, params)
      }
   }

   private fun mapAnnotationParams(annotation: TaxiParser.AnnotationContext): Map<String, Any> {
      return when {
         annotation.elementValue() != null -> mapOf("value" to annotation.elementValue().literal().value())
         annotation.elementValuePairs() != null -> mapElementValuePairs(annotation.elementValuePairs())
         else -> // No params specified
            emptyMap()
      }
   }

   private fun mapElementValuePairs(pairs: TaxiParser.ElementValuePairsContext): Map<String, Any> {
      return pairs.elementValuePair()?.map {
         it.Identifier().text to it.elementValue().literal()?.value()!!
      }?.toMap() ?: emptyMap()
   }

   private fun parseTypeOrVoid(namespace: Namespace, returnType: TaxiParser.OperationReturnTypeContext?): Either<CompilationError, Type> {
      return if (returnType == null) {
         VoidType.VOID.right()
      } else {
         parseType(namespace, returnType.typeType())
      }
   }

   internal fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      val typeOrError = typeOrError(namespace, typeType)
      return typeOrError.flatMap { type ->
         parseTypeFormat(typeType).flatMap { format ->
            if (typeType.listType() != null) {
               if (format != null) {
                  Either.left(CompilationError(typeType.start, "It is invalid to declare a format on an array"))
               } else {
                  Either.right(ArrayType(type, typeType.toCompilationUnit()))
               }
            } else {
               if (format != null) {
                  generateFormattedSubtype(type, format, typeType)
               } else {
                  Either.right(type)
               }
            }
         }
      }
   }

   private fun parseType(namespace: Namespace,
                         formula: Formula,
                         typeType: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      val typeOrError = typeOrError(namespace, typeType)
      return typeOrError.flatMap { type ->
         if (typeType.listType() != null) {
            Either.left(CompilationError(typeType.start,
               "It is invalid to declare calculated type on an array"))
         } else {
            generateCalculatedFieldType(type, formula)
         }
      }
   }

   private fun typeOrError(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      return when {
         typeType.aliasedType() != null -> compileInlineTypeAlias(namespace, typeType)
         typeType.classOrInterfaceType() != null -> resolveUserType(namespace, typeType.classOrInterfaceType())
         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text).right()
         else -> throw IllegalArgumentException()
      }
   }

   private fun generateFormattedSubtype(type: Type, format: List<String>, typeType: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      val formattedTypeName = QualifiedName.from(type.qualifiedName).let { originalTypeName ->
         val hash = Hashing.sha256().hashString(format.joinToString(), Charset.defaultCharset()).toString().takeLast(6)
         originalTypeName.copy(typeName = "Formatted${originalTypeName.typeName}_$hash")
      }

      return if (typeSystem.contains(formattedTypeName.fullyQualifiedName)) {
         Either.right(typeSystem.getType(formattedTypeName.fullyQualifiedName))
      } else {
         val formattedType = ObjectType(
            formattedTypeName.fullyQualifiedName,
            ObjectTypeDefinition(
               emptySet(),
               inheritsFrom = setOf(type),
               format = format,
               formattedInstanceOfType = type,
               compilationUnit = CompilationUnit.generatedFor(type)
            )
         )
         typeSystem.register(formattedType)
         Either.right(formattedType)
      }


   }

   private fun generateCalculatedFieldType(type: Type, formula: Formula): Either<CompilationError, Type> {
      val operands = formula.operandFields
      val calculatedTypeName = QualifiedName.from(type.qualifiedName).let { originalTypeName ->
         val hash = Hashing.sha256().hashString(operands.sortedBy { it.fullyQualifiedName }.joinToString("_"), Charset.defaultCharset()).toString().takeLast(6)
         originalTypeName.copy(typeName = "Calculated${originalTypeName.typeName}_$hash")
      }

      return if (typeSystem.contains(calculatedTypeName.fullyQualifiedName)) {
         Either.right(typeSystem.getType(calculatedTypeName.fullyQualifiedName))
      } else {
         val formattedType = ObjectType(
            calculatedTypeName.fullyQualifiedName,
            ObjectTypeDefinition(
               emptySet(),
               inheritsFrom = setOf(type),
               calculatedInstanceOfType = type,
               calculation = formula,
               compilationUnit = CompilationUnit.generatedFor(type)
            )
         )
         typeSystem.register(formattedType)
         Either.right(formattedType)
      }
   }

   private fun parseTypeFormat(typeType: TaxiParser.TypeTypeContext): Either<CompilationError, List<String>?> {
      val formatExpressions = typeType.parameterConstraint()?.parameterConstraintExpressionList()?.parameterConstraintExpression()
         ?.mapNotNull { it.propertyFormatExpression() } ?: emptyList()
      return when {
         formatExpressions.isEmpty() -> Either.right(null)
         else -> Either.right(formatExpressions.map { stringLiteralValue(it.StringLiteral()) })
      }
   }

   /**
    * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
    * rather than those declared explicitly (type alias PersonFirstName as String)
    */
   private fun compileInlineTypeAlias(namespace: Namespace, aliasTypeDefinition: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      return parseType(namespace, aliasTypeDefinition.aliasedType().typeType()).map { aliasedType ->
         val declaredTypeName = aliasTypeDefinition.classOrInterfaceType().Identifier().text()
         val typeAliasName = if (declaredTypeName.contains(".")) {
            QualifiedNameParser.parse(declaredTypeName)
         } else {
            QualifiedName(namespace, declaredTypeName)
         }
         // Annotations not supported on Inline type aliases
         val annotations = emptyList<Annotation>()
         val typeAlias = TypeAlias(typeAliasName.toString(), TypeAliasDefinition(aliasedType, annotations, aliasTypeDefinition.toCompilationUnit()))
         typeSystem.register(typeAlias)
         typeAlias
      }
   }

   private fun resolveUserType(namespace: Namespace, requestedTypeName: String, imports: List<QualifiedName>, context: ParserRuleContext): Either<CompilationError, Type> {
      return resolveUserToken(namespace, requestedTypeName, imports, context) { qualifiedTypeName ->
         if (tokens.unparsedTypes.contains(qualifiedTypeName)) {
            compileToken(qualifiedTypeName)
            typeSystem.getTypeOrError(qualifiedTypeName, context)
         } else {
            null
         }
      }.map { it as Type }
   }

   private fun resolveUserToken(
      namespace: Namespace,
      requestedTypeName: String,
      imports: List<QualifiedName>,
      context: ParserRuleContext,
      // Method which takes the resolved qualified name, and checks to see if there is an unparsed token.
      // If so, the method should compile the token and return the compilation result in the either.
      // If not, then the method should return false.
      unparsedCheckAndCompile: (String) -> Either<CompilationError, ImportableToken>?
   ): Either<CompilationError, ImportableToken> {
      return attemptToLookupTypeByName(namespace, requestedTypeName, context).flatMap { qualifiedTypeName ->
         if (typeSystem.contains(qualifiedTypeName)) {
            return@flatMap typeSystem.getTokenOrError(qualifiedTypeName, context)
               .flatMap { importableToken ->
                  if (importableToken is DefinableToken<*> && !importableToken.isDefined) {
                     unparsedCheckAndCompile(qualifiedTypeName) ?: Either.right(importableToken)
                  } else {
                     Either.right(importableToken)
                  }
               }
//               .flatMap { importableToken ->
//               if (importableToken is DefinableToken<*> && !importableToken.isDefined) {
//                  f = unparsedCheckAndCompile(qualifiedTypeName)
//               } else {
//                  Either.right(importableToken)
//               }

         }

         // Check to see if the token is unparsed, and
         // ccmpile if so
         val compilationResult = unparsedCheckAndCompile(qualifiedTypeName)
         if (compilationResult != null) {
            return@flatMap compilationResult!!
         }

         // Note: Use requestedTypeName, as qualifying it to the local namespace didn't help
         val error = { CompilationError(context.start, ErrorMessages.unresolvedType(requestedTypeName), context.source().sourceName) }

         val requestedNameIsQualified = requestedTypeName.contains(".")
         if (!requestedNameIsQualified) {
            val importedTypeName = imports.firstOrNull { it.typeName == requestedTypeName }
            if (importedTypeName != null) {
               typeSystem.getTokenOrError(importedTypeName.parameterizedName, context)
            } else {
               Either.left(error())
            }
         } else {
            Either.left(error())
         }
      }
   }

   private fun resolveUserType(namespace: Namespace, classType: TaxiParser.ClassOrInterfaceTypeContext): Either<CompilationError, Type> {
      return resolveUserType(namespace, classType.Identifier().text(), classType)
   }

   private fun resolveUserType(namespace: Namespace, requestedTypeName: String, context: ParserRuleContext): Either<CompilationError, Type> {
      return resolveUserType(namespace, requestedTypeName, importsInSource(context), context)
   }

   private fun resolveFunction(requestedFunctionName: String, context: ParserRuleContext): Either<CompilationError, Function> {
      val namespace = context.findNamespace()
      return resolveUserToken(namespace, requestedFunctionName, importsInSource(context), context) { qualifiedName ->
         if (tokens.unparsedFunctions.contains(qualifiedName)) {
            compileFunction(tokens.unparsedFunctions[qualifiedName]!!, qualifiedName)
         } else {
            null
         }
      }.map { it as Function }
   }

   private fun importsInSource(context: ParserRuleContext): List<QualifiedName> {
      return tokens.importedTypeNamesInSource(context.source().normalizedSourceName)
   }


   private fun compileEnum(namespace: Namespace, typeName: String, ctx: TaxiParser.EnumDeclarationContext): Either<List<CompilationError>, EnumType> {
      return compileEnumValues(namespace, typeName, ctx.enumConstants())
         .map { enumValues ->
            val annotations = collateAnnotations(ctx.annotation())
            val basePrimitive = deriveEnumBaseType(enumValues)
            val inherits = parseEnumInheritance(namespace, ctx.enumInheritedType())
            val isLenient = ctx.lenientKeyword() != null
            val enumType = EnumType(typeName, EnumDefinition(
               enumValues,
               annotations,
               ctx.toCompilationUnit(),
               inheritsFrom = if (inherits != null) setOf(inherits) else emptySet(),
               typeDoc = parseTypeDoc(ctx.typeDoc()),
               basePrimitive = basePrimitive,
               isLenient = isLenient
            ))
            typeSystem.register(enumType)
            enumType
         }


   }

   private fun deriveEnumBaseType(enumValues: List<EnumValue>): PrimitiveType {
      val distinctValueTypes = enumValues.map { it.value::class }.distinct()
      return if (distinctValueTypes.size != 1) {
         PrimitiveType.STRING
      } else {
         when (val type = distinctValueTypes.first()) {
            String::class -> PrimitiveType.STRING
            Int::class -> PrimitiveType.INTEGER
            else -> {
               log().warn("Enums of type ${type.simpleName} are not supported, falling back to String")
               PrimitiveType.STRING
            }
         }
      }
   }

   private fun compileEnumValueExtensions(enumConstants: TaxiParser.EnumConstantExtensionsContext?): List<EnumValueExtension> {
      return enumConstants?.enumConstantExtension()?.map { constantExtension ->
         EnumValueExtension(
            constantExtension.Identifier().text,
            collateAnnotations(constantExtension.annotation()),
            emptyList(), // Currently, we grab all the synonyms later on
            parseTypeDoc(constantExtension.typeDoc()),
            constantExtension.toCompilationUnit()
         )
      } ?: emptyList()
   }

   private fun compileEnumValues(namespace: Namespace, enumQualifiedName: String, enumConstants: TaxiParser.EnumConstantsContext?): Either<List<CompilationError>, List<EnumValue>> {
      @Suppress("IfThenToElvis")
      return if (enumConstants == null) {
         Either.right(emptyList())
      } else {
         enumConstants.enumConstant().map { enumConstant ->
            val annotations = collateAnnotations(enumConstant.annotation())
            val name = enumConstant.Identifier().text
            val qualifiedName = "$enumQualifiedName.$name"
            val value = enumConstant.enumValue()?.literal()?.value() ?: name
            val isDefault = enumConstant.defaultKeyword() != null
            parseSynonyms(namespace, enumConstant).map { synonyms ->
               synonymRegistry.registerSynonyms(qualifiedName, synonyms, enumConstant)
               EnumValue(name, value, qualifiedName, annotations, synonyms, parseTypeDoc(enumConstant.typeDoc()), isDefault)
            }
         }.invertEitherList()
            .mapLeft { listOfLists: List<List<CompilationError>> -> listOfLists.flatten() }
            .flatMap { enumValues -> validateOnlySingleDefaultEnumValuePresent(enumValues, enumConstants) }
      }

   }

   private fun validateOnlySingleDefaultEnumValuePresent(enumValues: List<EnumValue>, token: TaxiParser.EnumConstantsContext): Either<List<CompilationError>, List<EnumValue>> {
      val defaults = enumValues.filter { it.isDefault }
      if (defaults.size > 1) {
         return Either.left(listOf(CompilationError(token.start, "Cannot declare multiple default values - found ${defaults.joinToString { it.name }}")))
      } else {
         return Either.right(enumValues)
      }
   }

   /**
    * Returns a set of references to enum values that this enum value declares a synonym to.
    * Note that because of compilation order, a result from this method guarantees that the
    * enum exists, but NOT that the value on the enum exists.
    * That's handled later when synonyms are resolved.
    */
   private fun parseSynonyms(namespace: Namespace, enumConstant: TaxiParser.EnumConstantContext): Either<List<CompilationError>, List<EnumValueQualifiedName>> {
      val declaredSynonyms = enumConstant.enumSynonymDeclaration()?.enumSynonymSingleDeclaration()?.let { listOf(it.qualifiedName()) }
         ?: enumConstant.enumSynonymDeclaration()?.enumSynonymDeclarationList()?.qualifiedName()
         ?: emptyList()
      return declaredSynonyms.map { synonym ->
         val (enumName, enumValueName) = Enums.splitEnumValueQualifiedName(synonym.Identifier().text())
         // TODO : I'm concerned this might cause stack overflow / loops.
         // Will wait and see
         resolveUserType(namespace, enumName.parameterizedName, importsInSource(enumConstant), enumConstant)
            .flatMap { enumType ->
               if (enumType is EnumType) {
                  // Note - at this point we can be sure that the Enum exists, but it's values
                  // haven't yet been parsed, so we can assert anything that the enum value we're linking to
                  // exists
                  // We'll do that later
                  Either.right(Enums.enumValue(enumType.toQualifiedName(), enumValueName))
               } else {
                  Either.left(CompilationError(enumConstant.start, "${enumType.qualifiedName} is not an Enum", enumConstant.source().normalizedSourceName))
               }
            }

      }.invertEitherList()
   }


   private fun compileEnumExtension(namespace: Namespace, typeRule: TaxiParser.EnumExtensionDeclarationContext): CompilationError? {
      val enumValues = compileEnumValueExtensions(typeRule.enumConstantExtensions())
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc())

      return attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule)
         .flatMap { typeName ->
            val enum = typeSystem.getType(typeName) as EnumType
            enum.addExtension(EnumExtension(enumValues, annotations, typeRule.toCompilationUnit(), typeDoc = typeDoc)).toCompilationError(typeRule.start)
         }.errorOrNull()
   }

   private fun parseTypeDoc(content: TaxiParser.TypeDocContext?): String? {
      return parseTypeDoc(content?.source()?.content)
   }

   private fun compileFunctions() {
      val compiledFunctions = this.tokens.unparsedFunctions.map { (qualifiedName, namespaceAndParserContext) ->
         compileFunction(namespaceAndParserContext, qualifiedName)
      }.reportAndRemoveErrors()
   }

   private fun compileFunction(namespaceAndParserContext: Pair<Namespace, TaxiParser.FunctionDeclarationContext>, qualifiedName: String): Either<CompilationError, Function> {
      if (typeSystem.isDefined(qualifiedName)) {
         // The function may have already been compiled
         // if it's been used inline.
         // That's ok, we can just return it out of the type system
         return Either.right(typeSystem.getFunction(qualifiedName))
      }
      val (namespace, functionToken) = namespaceAndParserContext
      return parseType(namespace, functionToken.typeType()).map { returnType ->
         val parameters = functionToken.operationParameterList()?.operationParameter()?.map { parameterDefinition ->
            parseParameter(namespace, parameterDefinition)
         }?.reportAndRemoveErrorList() ?: emptyList()

         val function = Function(qualifiedName,
            FunctionDefinition(
               parameters, returnType, functionToken.toCompilationUnit())
         )
         this.functions.add(function)
         this.typeSystem.register(function)

         function
      }
   }

   private fun compileServices() {
      val services = this.tokens.unparsedServices.map { (qualifiedName, serviceTokenPair) ->
         val (namespace, serviceToken) = serviceTokenPair
         val serviceDoc = parseTypeDoc(serviceToken.typeDoc())
         val methods = serviceToken.serviceBody().serviceOperationDeclaration().map { operationDeclaration ->
            val signature = operationDeclaration.operationSignature()
            parseTypeOrVoid(namespace, signature.operationReturnType()).map { returnType ->
               val scope = operationDeclaration.operationScope()?.Identifier()?.text
               val operationParameters = signature.parameters().map { operationParameterContext ->
                  parseParameter(namespace, operationParameterContext)
               }.reportAndRemoveErrorList()

               parseOperationContract(operationDeclaration, returnType, namespace).map { contract ->
                  Operation(name = signature.Identifier().text,
                     scope = scope,
                     annotations = collateAnnotations(operationDeclaration.annotation()),
                     parameters = operationParameters,
                     compilationUnits = listOf(operationDeclaration.toCompilationUnit()),
                     returnType = returnType,
                     contract = contract,
                     typeDoc = parseTypeDoc(operationDeclaration.typeDoc())
                  )
               }

            }
         }.reportAndRemoveErrors()
            .reportAndRemoveErrorList() // The double chaining seems to work, not a mistake, but not neccessarily readable.

         Service(
            qualifiedName,
            methods,
            collateAnnotations(serviceToken.annotation()),
            listOf(serviceToken.toCompilationUnit()),
            serviceDoc
         )
      }
      this.services.addAll(services)
   }

   private fun parseParameter(namespace: Namespace, operationParameterContext: TaxiParser.OperationParameterContext): Either<List<CompilationError>, Parameter> {
      return parseType(namespace, operationParameterContext.typeType())
         .mapLeft { listOf(it) }
         .flatMap { paramType ->
            mapConstraints(operationParameterContext.typeType(), paramType, namespace).map { constraints ->
               val isVarargs = operationParameterContext.varargMarker() != null
               Parameter(collateAnnotations(operationParameterContext.annotation()), paramType,
                  name = operationParameterContext.parameterName()?.Identifier()?.text,
                  constraints = constraints,
                  isVarArg = isVarargs)
            }
         }
   }

   private fun parseOperationContract(operationDeclaration: TaxiParser.ServiceOperationDeclarationContext, returnType: Type, namespace: Namespace): Either<List<CompilationError>, OperationContract?> {
      val signature = operationDeclaration.operationSignature()
      val constraintList = signature.operationReturnType()
         ?.typeType()
         ?.parameterConstraint()
         ?.parameterConstraintExpressionList()
         ?: return Either.right(null)

      return OperationConstraintConverter(
         constraintList,
         returnType,
         typeResolver(namespace)
      ).constraints().map { constraints ->
         OperationContract(returnType, constraints)
      }
   }

   private fun mapConstraints(typeType: TaxiParser.TypeTypeContext, paramType: Type, namespace: Namespace): Either<List<CompilationError>, List<Constraint>> {
      if (typeType.parameterConstraint() == null) {
         return Either.right(emptyList())
      }
      return OperationConstraintConverter(typeType.parameterConstraint()
         .parameterConstraintExpressionList(),
         paramType, typeResolver(namespace)).constraints()
   }

   private fun compilePolicies() {
      val compiledPolicies = this.tokens.unparsedPolicies.map { (name, namespaceTokenPair) ->
         val (namespace, token) = namespaceTokenPair

         parseType(namespace, token.typeType()).map { targetType ->
            val annotations = emptyList<Annotation>() // TODO
            val ruleSets = compilePolicyRulesets(namespace, token)
            Policy(
               name,
               targetType,
               ruleSets,
               annotations,
               compilationUnits = listOf(token.toCompilationUnit())
            )
         }
      }.reportAndRemoveErrors()
      this.policies.addAll(compiledPolicies)
   }

   fun typeResolver(namespace: Namespace): NamespaceQualifiedTypeResolver {
      return object : NamespaceQualifiedTypeResolver {
         override val namespace: String = namespace

         override fun resolve(context: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
            return parseType(namespace, context)
         }

         override fun resolve(requestedTypeName: String, context: ParserRuleContext): Either<CompilationError, Type> {
            return resolveUserType(namespace, requestedTypeName, context)
         }
      }
   }

   private fun compilePolicyRulesets(namespace: String, token: TaxiParser.PolicyDeclarationContext): List<RuleSet> {
      return token.policyRuleSet().map {
         compilePolicyRuleset(namespace, it)
      }
   }

   private fun compilePolicyRuleset(namespace: String, token: TaxiParser.PolicyRuleSetContext): RuleSet {
      val operationType = token.policyOperationType().Identifier()?.text
      val operationScope = OperationScope.parse(token.policyScope()?.text)
      val scope = PolicyScope.from(operationType, operationScope)
      val statements = if (token.policyBody() != null) {
         token.policyBody().policyStatement().map { compilePolicyStatement(namespace, it) }
      } else {
         listOf(PolicyStatement(ElseCondition(), Instructions.parse(token.policyInstruction()), token.toCompilationUnit()))
      }
      return RuleSet(scope, statements)
   }

   private fun compilePolicyStatement(namespace: String, token: TaxiParser.PolicyStatementContext): PolicyStatement {
      val (condition, instruction) = compileCondition(namespace, token)
      return PolicyStatement(condition, instruction, token.toCompilationUnit())
   }

   private fun compileCondition(namespace: String, token: TaxiParser.PolicyStatementContext): Pair<Condition, Instruction> {
      return when {
         token.policyCase() != null -> compileCaseCondition(namespace, token.policyCase())
         token.policyElse() != null -> ElseCondition() to Instructions.parse(token.policyElse().policyInstruction())
         else -> error("Invalid condition is neither a case nor an else")
      }
   }

   private fun compileCaseCondition(namespace: String, case: TaxiParser.PolicyCaseContext): Pair<Condition, Instruction> {
      val typeResolver = typeResolver(namespace)
      val condition = CaseCondition(
         Subjects.parse(case.policyExpression(0), typeResolver),
         Operator.parse(case.policyOperator().text),
         Subjects.parse(case.policyExpression(1), typeResolver)
      )
      val instruction = Instructions.parse(case.policyInstruction())
      return condition to instruction
   }

}

