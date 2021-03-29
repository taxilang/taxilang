package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
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
import lang.taxi.functions.FunctionDefinition
import lang.taxi.query.TaxiQlQuery
import lang.taxi.linter.Linter
import lang.taxi.services.FilterCapability
import lang.taxi.services.QueryOperation
import lang.taxi.services.QueryOperationCapability
import lang.taxi.services.SimpleQueryCapability
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.types.View.Companion.JoinAnnotationName
import lang.taxi.utils.errorOrNull
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import lang.taxi.utils.wrapErrorsInList
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Base64

class TokenProcessor(
   val tokens: Tokens,
   importSources: List<TaxiDocument> = emptyList(),
   collectImports: Boolean = true,
   val typeChecker: TypeChecker,
   private val linter: Linter
) {

   companion object {
      fun unescape(text: String): String {
         return text.removeSurrounding("`")
      }

   }

   constructor(tokens: Tokens, collectImports: Boolean, typeChecker: TypeChecker, linter: Linter = Linter.empty()) : this(
      tokens,
      emptyList(),
      collectImports,
      typeChecker,
      linter
   )

   private var createEmptyTypesPerformed: Boolean = false
   private val typeSystem: TypeSystem
   private val synonymRegistry: SynonymRegistry<ParserRuleContext>
   private val services = mutableListOf<Service>()
   private val policies = mutableListOf<Policy>()
   private val functions = mutableListOf<Function>()
   private val annotations = mutableListOf<Annotation>()
   private val views = mutableListOf<View>()
   private val constraintValidator = ConstraintValidator()

   private val errors = mutableListOf<CompilationError>()

//   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)
//   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)

   private val tokensCurrentlyCompiling = mutableSetOf<String>()
   private val defaultValueParser = DefaultValueParser()
   private val queries = mutableListOf<TaxiQlQuery>()

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
      return errors to TaxiDocument(types, services.toSet(), policies.toSet(), functions.toSet(), annotations.toSet(), views.toSet())
   }

   fun buildQueries(): Pair<List<CompilationError>, List<TaxiQlQuery>> {
      compile()
      return errors to queries
   }

   // Primarily for language server tooling, rather than
   // compile time - though it should be safe to use in all scnearios
   fun lookupTypeByName(contextRule: TaxiParser.TypeTypeContext): String {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return lookupTypeByName(namespace, contextRule)
   }

   fun lookupTypeByName(text: String, contextRule: ParserRuleContext): Either<List<CompilationError>, String> {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return attemptToLookupTypeByName(namespace, text, contextRule).wrapErrorsInList()
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
               if (fieldDeclaration.simpleFieldDeclaration().typeType() != null && fieldDeclaration.simpleFieldDeclaration().typeType().aliasedType() != null) {
                  // This is an inline type alias
                  lookupTypeByName(namespace, memberDeclaration.fieldDeclaration().simpleFieldDeclaration().typeType())
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
      return if (PrimitiveType.isPrimitiveType(type.text)) {
         PrimitiveType.fromDeclaration(type.text).qualifiedName
      } else {
         lookupTypeByName(namespace, type.classOrInterfaceType().text, importsInSource(type))
      }
   }


   internal fun attemptToLookupTypeByName(
      namespace: Namespace,
      name: String,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL
   ): Either<CompilationError, String> {
      return try {
         Either.right(lookupTypeByName(namespace, name, importsInSource(context), symbolKind))
      } catch (e: AmbiguousNameException) {
         Either.left(CompilationError(context.start, e.message!!, context.source().normalizedSourceName))
      }
   }

   // THe whole additionalImports thing is for when we're
   // accessing prior to compiling (ie., in the language server).
   // During normal compilation, don't need to pass anything
   @Deprecated("call attemptToQualify, so errors are caught property")
   private fun lookupTypeByName(
      namespace: Namespace,
      name: String,
      importsInSource: List<QualifiedName>,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL
   ): String {
      return typeSystem.qualify(namespace, name, importsInSource, symbolKind)

   }

   internal fun getType(namespace: Namespace, name: String, context: ParserRuleContext): Either<List<CompilationError>, Type> {
      return attemptToLookupTypeByName(namespace, name, context).map { qfn ->
         typeSystem.getType(qfn)
      }.wrapErrorsInList()
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
      validaCaseWhenLogicalExpressions()

      // Queries
      compileQueries()
      //
      compileViews()
   }

   private fun compileViews() {
      val viewProcessor = ViewProcessor(this)
      this.tokens.unparsedViews.map { entry ->
         viewProcessor.compileView(entry.key, entry.value.first, entry.value.second)
      }.invertEitherList()
         .flattenErrors()
         .collectErrors(errors)
         .map { this.views.addAll(it) }
   }

   private fun compileQueries() {
      this.tokens.anonymousQueries.forEach { (qualifiedName, anonymousQueryContex) ->
         QueryCompiler(this)
            .parseQueryBody(qualifiedName, mapOf(), anonymousQueryContex.queryBody())
            .mapLeft { compilationErrors -> errors.addAll(compilationErrors) }
            .map { taxiQlQuery ->
               queries.add(taxiQlQuery)
            }
      }

      this.tokens.namedQueries.forEach { (qualifiedName, namedQueryContext) ->
         val queryName = namedQueryContext.queryName().Identifier().text
         val parametersOrErrors = namedQueryContext.queryName().queryParameters()?.queryParamList()?.queryParam()?.map { queryParam ->
            val parameterName = queryParam.Identifier().text
            val queryParameter: Either<List<CompilationError>, Pair<String, QualifiedName>> =
               typeOrError(namedQueryContext.findNamespace(), queryParam.typeType()).map { parameterType ->
                  parameterName to parameterType.toQualifiedName()
               }
            queryParameter
         }?.invertEitherList() ?: Either.right(emptyList())
         parametersOrErrors
            .mapLeft { compilationErrors -> errors.addAll(compilationErrors.flatten()) }
            .map { parameters ->
               QueryCompiler(this)
                  .parseQueryBody(queryName, parameters.toMap(), namedQueryContext.queryBody())
                  .mapLeft { compilationErrors -> errors.addAll(compilationErrors) }
                  .map { taxiQlQuery -> queries.add(taxiQlQuery) }
            }
      }
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
                     EnumValueExtension(
                        enumValueName,
                        emptyList(),
                        listOf(synonym),
                        compilationUnit = context.toCompilationUnit()
                     )
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

   private fun registerErrorsForInvalidSynonyms(
      enum: EnumType,
      enumValueName: String,
      parserContext: ParserRuleContext
   ): Boolean {
      return if (!enum.hasName(enumValueName)) {
         errors.add(
            CompilationError(
               parserContext.start,
               "$enumValueName is not defined on type ${enum.qualifiedName}"
            )
         )
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

   private fun validaCaseWhenLogicalExpressions() {
      typeSystem.typeList().filterIsInstance<ObjectType>()
         .forEach { type ->
            type
               .allFields
               .filter {
                  it.accessor is ConditionalAccessor &&
                     (it.accessor as ConditionalAccessor).expression is WhenFieldSetCondition
               }
               .forEach {
                  val whenFieldSetCondition = ((it.accessor as ConditionalAccessor).expression as WhenFieldSetCondition)
                  val logicalExpressions = whenFieldSetCondition
                     .cases.map { aCase -> aCase.matchExpression }
                     .filterIsInstance<LogicalExpression>()
                  when {
                     logicalExpressions.isNotEmpty() && whenFieldSetCondition.selectorExpression !is EmptyReferenceSelector -> {
                        errors.add(
                           CompilationError(
                              type,
                              "when case for ${it.name} in ${type.qualifiedName} cannot have reference selector use when { .. } syntax"
                           )
                        )
                     }
                     whenFieldSetCondition.selectorExpression is EmptyReferenceSelector &&
                        whenFieldSetCondition.cases.map { it.matchExpression }.filter { it !is ElseMatchExpression }
                           .any { it !is LogicalExpression } -> {
                        errors.add(
                           CompilationError(
                              type,
                              "when case for ${it.name} in ${type.qualifiedName} can only logical expression when cases"
                           )
                        )
                     }
                     else -> validateLogicalExpression(type, typeSystem, it, logicalExpressions)
                  }
               }
         }
   }

   private fun validateLogicalExpression(
      type: ObjectType,
      typeSystem: TypeSystem,
      field: Field,
      logicalExpressions: List<LogicalExpression>
   ) {
      logicalExpressions.forEach {
         when (it) {
            is ComparisonExpression -> validateComparisonExpression(it, type)
            is AndExpression -> validateLogicalExpression(type, typeSystem, field, listOf(it.left, it.right))
            is OrExpression -> validateLogicalExpression(type, typeSystem, field, listOf(it.left, it.right))
         }
      }
   }

   private fun validateComparisonExpression(comparisonExpression: ComparisonExpression, type: ObjectType) {
      val right = comparisonExpression.right
      val left = comparisonExpression.left
      when {
         right is FieldReferenceEntity && left is FieldReferenceEntity -> {
            validateFieldReferenceEntity(right, type)
            validateFieldReferenceEntity(left, type)
         }
         right is ConstantEntity && left is FieldReferenceEntity -> {
            val leftField = validateFieldReferenceEntity(left, type)
            validateConstantEntityAgainstField(leftField, right, type, comparisonExpression.operator)
         }

         right is FieldReferenceEntity && left is ConstantEntity -> {
            val rightField = validateFieldReferenceEntity(right, type)
            validateConstantEntityAgainstField(rightField, left, type, comparisonExpression.operator)
         }
      }
   }

   private fun validateFieldReferenceEntity(fieldReferenceEntity: FieldReferenceEntity, type: ObjectType): Field? {
      val referencedField = type.allFields.firstOrNull { field -> field.name == fieldReferenceEntity.fieldName }
      if (referencedField == null) {
         errors.add(CompilationError(type, "${fieldReferenceEntity.fieldName} is not a field of ${type.qualifiedName}"))
      } else {
         if (referencedField.type.basePrimitive == null) {
            errors.add(
               CompilationError(
                  type,
                  "${fieldReferenceEntity.fieldName} is not a field of ${type.qualifiedName}"
               )
            )
         }
      }
      return referencedField
   }

   private fun validateConstantEntityAgainstField(
      field: Field?,
      constantEntity: ConstantEntity,
      type: ObjectType,
      operator: ComparisonOperator
   ) {
      if (field?.type?.basePrimitive != PrimitiveType.DECIMAL &&
         field?.type?.basePrimitive != PrimitiveType.INTEGER &&
         field?.type?.basePrimitive != PrimitiveType.STRING
      ) {
         errors.add(
            CompilationError(
               type,
               "${field?.name} should be a String, Int or Decimal based field of ${type.qualifiedName}"
            )
         )
      }
      if (constantEntity.value is String && field?.type?.basePrimitive != PrimitiveType.STRING) {
         errors.add(CompilationError(type, "${field?.name} is not a String based field of ${type.qualifiedName}"))
      }

      if (constantEntity.value is Number && (field?.type?.basePrimitive != PrimitiveType.DECIMAL && field?.type?.basePrimitive != PrimitiveType.INTEGER)) {
         errors.add(CompilationError(type, "${field?.name} is not a numeric based field of ${type.qualifiedName}"))
      }

      if (!operator.applicablePrimitives.contains(field?.type?.basePrimitive)) {
         errors.add(
            CompilationError(
               type,
               "${operator.symbol} is not applicable to ${field?.name} field of ${type.qualifiedName}"
            )
         )

      }
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
            is TaxiParser.AnnotationTypeDeclarationContext -> typeSystem.register(AnnotationType.undefined(tokenName))
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

      enumUnparsedTypes
         .plus(nonEnumParsedTypes)
         .forEach { (tokenName, _) ->
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
         val type = when (tokenRule) {
            is TaxiParser.TypeDeclarationContext -> compileType(namespace, tokenName, tokenRule)
            is TaxiParser.AnnotationTypeDeclarationContext -> compileAnnotationType(tokenName, namespace, tokenRule)
            is TaxiParser.EnumDeclarationContext -> compileEnum(namespace, tokenName, tokenRule).collectErrors(errors)
            is TaxiParser.TypeAliasDeclarationContext -> compileTypeAlias(
               namespace,
               tokenName,
               tokenRule
            ).collectErrors(errors)
            // TODO : This is a bit broad - assuming that all typeType's that hit this
            // line will be a TypeAlias inline.  It could be a normal field declaration.
            is TaxiParser.TypeTypeContext -> compileInlineTypeAlias(namespace, tokenRule).collectErrors(errors)
            else -> TODO("Not handled: $tokenRule")
         }.map { type ->
            this.errors.addAll(linter.lint(type))
            type
         }
      } finally {
         tokensCurrentlyCompiling.remove(tokenName)
      }
   }

   private fun applyLinterRules(type: Type): Collection<CompilationError> {
      TODO("Not yet implemented")
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
      this.errors.addAll(errors)

   }

   private fun compileTypeAliasExtension(
      namespace: Namespace,
      typeRule: TaxiParser.TypeAliasExtensionDeclarationContext
   ): CompilationError? {
      return attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule).flatMap { typeName ->
         val type = typeSystem.getType(typeName) as TypeAlias
         val annotations = collateAnnotations(typeRule.annotation())
         val typeDoc = parseTypeDoc(typeRule.typeDoc())
         type.addExtension(TypeAliasExtension(annotations, typeRule.toCompilationUnit(), typeDoc))
            .mapLeft { it.toCompilationError(typeRule.start) }
      }.errorOrNull()
   }

   private fun compileTypeExtension(
      namespace: Namespace,
      typeRule: TaxiParser.TypeExtensionDeclarationContext
   ): CompilationError? {
      val typeName =
         when (val typeNameEither = attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule)) {
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
            ?.defaultDefinition()
            ?.qualifiedName()?.let { enumDefaultValue ->
               assertEnumDefaultValueCompatibility(
                  refinedType!! as EnumType,
                  enumDefaultValue.text,
                  fieldName,
                  typeRule
               )
            }

         val constantValue = enumConstantValue ?: member
            ?.typeExtensionFieldDeclaration()
            ?.typeExtensionFieldTypeRefinement()
            ?.constantDeclaration()
            ?.defaultDefinition()
            ?.let { defaultDefinitionContext ->
               defaultValueParser.parseDefaultValue(defaultDefinitionContext, refinedType!!)
            }?.collectError(errors)?.getOrElse { null }

         FieldExtension(fieldName, fieldAnnotations, refinedType, constantValue)
      }
      val errorMessage =
         type.addExtension(ObjectTypeExtension(annotations, fieldExtensions, typeDoc, typeRule.toCompilationUnit()))
      return errorMessage
         .mapLeft { it.toCompilationError(typeRule.start) }
         .errorOrNull()
   }

   private fun assertTypesCompatible(
      originalType: Type,
      refinedType: Type,
      fieldName: String,
      typeName: String,
      typeRule: TaxiParser.TypeExtensionDeclarationContext
   ): Type {
      if (!refinedType.isAssignableTo(originalType)) {
         throw CompilationException(
            typeRule.start,
            "Cannot refine field '$fieldName' on $typeName to ${refinedType.qualifiedName} as it is incompatible with the existing type of ${originalType.qualifiedName}",
            typeRule.source().sourceName
         )
      }
      return refinedType
   }

   private fun assertLiteralDefaultValue(
      refinedType: Type,
      defaultValue: Any,
      fieldName: String,
      typeRule: TaxiParser.TypeExtensionDeclarationContext
   ) {
      val valid = when {
         refinedType.basePrimitive == PrimitiveType.STRING && defaultValue is String -> true
         refinedType.basePrimitive == PrimitiveType.DECIMAL && defaultValue is Number -> true
         refinedType.basePrimitive == PrimitiveType.INTEGER && defaultValue is Number -> true
         refinedType.basePrimitive == PrimitiveType.BOOLEAN && defaultValue is Boolean -> true
         else -> false
      }
      if (!valid) {
         throw CompilationException(
            typeRule.start,
            "Cannot set default value for field $fieldName as $defaultValue as it is not compatible with ${refinedType.basePrimitive?.qualifiedName}",
            typeRule.source().sourceName
         )
      }
   }

   private fun assertEnumDefaultValueCompatibility(
      enumType: EnumType,
      defaultValue: String,
      fieldName: String,
      typeRule: TaxiParser.TypeExtensionDeclarationContext
   ): EnumValue {
      return enumType.values.firstOrNull { enumValue -> enumValue.qualifiedName == defaultValue }
         ?: throw CompilationException(
            typeRule.start,
            "Cannot set default value for field $fieldName as $defaultValue as enum ${enumType.toQualifiedName().fullyQualifiedName} does not have corresponding value",
            typeRule.source().sourceName
         )
   }

   private fun compileTypeAlias(
      namespace: Namespace,
      tokenName: String,
      tokenRule: TaxiParser.TypeAliasDeclarationContext
   ): Either<List<CompilationError>, TypeAlias> {
      return parseType(namespace, tokenRule.aliasedType().typeType()).map { aliasedType ->
         val annotations = collateAnnotations(tokenRule.annotation())
         val definition = TypeAliasDefinition(
            aliasedType,
            annotations,
            tokenRule.toCompilationUnit(),
            typeDoc = parseTypeDoc(tokenRule.typeDoc())
         )
         val typeAlias = TypeAlias(tokenName, definition)
         this.typeSystem.register(typeAlias)
         typeAlias
      }
   }


//
//   private fun <T> Either<CompilationError, T>.collectError(): Either<ReportedError, T> {
//      return this.mapLeft { error ->
//         this@TokenProcessor.errors.add(error)
//         ReportedError(error)
//      }
//   }
//
//   private fun <T : Any> List<Either<List<CompilationError>, T>>.reportAndRemoveErrorList(): List<T> {
//      return this.mapNotNull { item ->
//         item.getOrHandle { errors ->
//            this@TokenProcessor.errors.addAll(errors)
//            null
//         }
//      }
//   }
//
//   private fun <T : Any> List<Either<CompilationError, T>>.reportAndRemoveErrors(): List<T> {
//     return reportAndRemoveErrors(this.er)
//   }
//
//   private fun <T : Any> Either<CompilationError, T>.reportIfCompilationError(): T? {
//      return this.getOrHandle { compilationError ->
//         this@TokenProcessor.errors.add(compilationError)
//         null
//      }
//   }

   fun List<TerminalNode>.text(): String {
      return this.joinToString(".")
   }

   private fun compileAnnotationType(
      name: String,
      namespace: Namespace,
      token: TaxiParser.AnnotationTypeDeclarationContext
   ): Either<List<CompilationError>, AnnotationType> {
      val typeWithFields = AnnotationTypeBodyContent(token.annotationTypeBody(), namespace)
      val fieldCompiler = FieldCompiler(
         this,
         typeWithFields,
         name,
         this.errors
      )
      val fields = fieldCompiler
         .compileAllFields()
         .map { field ->
            if (!field.type.inheritsFromPrimitive) {
               // Validate that annotation fields use primitive types.
               CompilationError(
                  token.start,
                  "Field ${field.name} declares an invalid type (${field.type.qualifiedName}). Only Strings, Numbers, Booleans or Enums are supported for annotation properties"
               ).left()
            } else {
               field.right()
            }
         }.reportAndRemoveErrors(errors)

      val annotations = collateAnnotations(token.annotation())
      val typeDoc = parseTypeDoc(token.typeDoc())
      val definition = AnnotationTypeDefinition(
         fields,
         annotations,
         typeDoc,
         token.toCompilationUnit()
      )
      return typeSystem.register(
         AnnotationType(
            name,
            definition
         )
      ).right()
   }

   private fun compileType(
      namespace: Namespace,
      typeName: String,
      ctx: TaxiParser.TypeDeclarationContext
   ): Either<List<CompilationError>, ObjectType> {
      val fields = ctx.typeBody()?.let { typeBody ->
         val typeBodyContext = TypeBodyContext(typeBody, namespace)
         FieldCompiler(this, typeBodyContext, typeName, this.errors)
            .compileAllFields()
      } ?: emptyList()

      val annotations = collateAnnotations(ctx.annotation())
      val modifiers = parseModifiers(ctx.typeModifier())
      val inherits = parseTypeInheritance(namespace, ctx.listOfInheritedTypes())
      val typeDoc = parseTypeDoc(ctx.typeDoc()?.source()?.content)
      return this.typeSystem.register(
         ObjectType(
            typeName, ObjectTypeDefinition(
            fields = fields.toSet(),
            annotations = annotations.toSet(),
            modifiers = modifiers,
            inheritsFrom = inherits,
            format = null,
            typeDoc = typeDoc,
            compilationUnit = ctx.toCompilationUnit()
         )
         )
      ).right()
   }

   private fun compileAnonymousType(
      namespace: Namespace,
      typeName: String,
      ctx: TaxiParser.TypeBodyContext,
      anonymousTypeResolutionContext: AnonymousTypeResolutionContext = AnonymousTypeResolutionContext()) {
      val fields = ctx.let { typeBody ->
         val typeBodyContext = TypeBodyContext(typeBody, namespace)
         FieldCompiler(this, typeBodyContext, typeName, this.errors, anonymousTypeResolutionContext)
            .compileAllFields()
      }

      val fieldsFromConcreteProjectedToType = anonymousTypeResolutionContext?.concreteProjectionTypeContext?.let {
         this.parseType(namespace, it).map { type ->
            (type as ObjectType?)?.let { objectType ->
               objectType.fields
            }
         }
      }

      val anonymousTypeFields = if (fieldsFromConcreteProjectedToType is Either.Right) {
         fieldsFromConcreteProjectedToType.b?.let { fields.plus(it) } ?: fields
      } else {
         fields
      }

      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(
         fields = anonymousTypeFields.toSet(),
         annotations = annotations.toSet(),
         modifiers = listOf(),
         inheritsFrom = emptySet(),
         format = null,
         compilationUnit = ctx.toCompilationUnit(),
         isAnonymous = true
      )))

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

   fun parseTypeInheritance(
      namespace: Namespace,
      listOfInheritedTypes: TaxiParser.ListOfInheritedTypesContext?
   ): Set<Type> {
      if (listOfInheritedTypes == null) return emptySet()
      return listOfInheritedTypes.typeType().mapNotNull { typeTypeContext ->

         parseInheritedType(namespace, typeTypeContext) {
            when (it) {
               is EnumType -> CompilationError(typeTypeContext.start, "A Type cannot inherit from an Enum").asList()
                  .left()
               else -> Either.right(it)
            }
         }

      }.toSet()
   }

   private fun parseEnumInheritance(
      namespace: Namespace,
      enumInheritedTypeContext: TaxiParser.EnumInheritedTypeContext?
   ): Type? {
      if (enumInheritedTypeContext == null) return null

      val typeTypeContext = enumInheritedTypeContext.typeType()
      return parseInheritedType(namespace, typeTypeContext) {
         when (it) {
            !is EnumType -> CompilationError(typeTypeContext.start, "An Enum can only inherit from an Enum").asList()
               .left()
            else -> Either.right(it)
         }
      }

   }

   private inline fun parseInheritedType(
      namespace: Namespace,
      typeTypeContext: TaxiParser.TypeTypeContext,
      filter: (Type) -> Either<List<CompilationError>, Type>
   ): Type? {
      val inheritedTypeOrError = parseType(namespace, typeTypeContext)

      val inheritedEnumTypeOrError = if (inheritedTypeOrError.isRight()) {
         filter(inheritedTypeOrError.getOrElse { null }!!)
      } else inheritedTypeOrError

      return inheritedEnumTypeOrError
         .getOrHandle {
            this.errors.addAll(it)
            null
         }
   }


   fun parseModifiers(typeModifier: MutableList<TaxiParser.TypeModifierContext>): List<Modifier> {
      return typeModifier.map { Modifier.fromToken(it.text) }
   }

   internal fun collateAnnotations(annotations: List<TaxiParser.AnnotationContext>): List<Annotation> {
      val result = annotations.map { annotation ->
         val annotationName = annotation.qualifiedName().text
         mapAnnotationParams(annotation).flatMap { annotationParameters ->
            val annotationType = resolveUserType(
               annotation.findNamespace(),
               annotationName,
               annotation,
               symbolKind = SymbolKind.ANNOTATION
            )
               .wrapErrorsInList()
            val constructedAnonymousAnnotation = Annotation(annotationName, annotationParameters)
            val resolvedAnnotation = when (annotationType) {
               is Either.Left -> constructedAnonymousAnnotation.right()
               is Either.Right -> annotationType.flatMap { type ->
                  if (type is AnnotationType) {
                     buildTypedAnnotation(type, annotation, annotationParameters)
                  } else {
                     // We used to throw an error here.
                     // However, if a model field has an annotation against a type that exists in it's own right, it ends
                     // up being resolved, even though that's not the intent.
                     // eg:
                     // namespace foo {
                     //    type Id inherits String
                     //    model Thing {
                     //       @Id
                     //       id : Id
                     //    }
                     // The above was valid for a long time, as annotations weren't compiled,
                     // and we shouldn't break that behaviour
                     constructedAnonymousAnnotation.right()
//                     listOf(CompilationError(annotation.start, "${type.qualifiedName} is not an annotation type")).left()
                  }
               }
            }
            resolvedAnnotation

         }
      }

      return result.reportAndRemoveErrorList(errors)
   }

   private fun buildTypedAnnotation(
      type: AnnotationType,
      annotation: TaxiParser.AnnotationContext,
      annotationParameters: Map<String, Any>
   ): Either<List<CompilationError>, Annotation> {
      val fieldErrors = type.fields.mapNotNull { field ->
         if (!annotationParameters.containsKey(field.name)) {
            CompilationError(
               annotation.start,
               "Annotation ${type.qualifiedName} requires member '${field.name}' which was not supplied"
            )
         } else {
            // TODO: validate types match.
            // Waiting until the existing branch on type safety is merged, and then will revisit
            null
         }
      }

      // Were there any parameters passed that we didn't expect?
      val unexpectedParamErrors =
         annotationParameters.filter { (parameterName, _) -> type.fields.none { field -> field.name == parameterName } }
            .map { (parameterName, _) ->
               CompilationError(
                  annotation.start,
                  "Unexpected property - '${parameterName}' is not a member of ${type.qualifiedName}"
               )
            }
      val allErrors = unexpectedParamErrors + fieldErrors
      return if (allErrors.isEmpty()) {
         Annotation(type, annotationParameters).right()
      } else {
         allErrors.left()
      }
   }

   private fun mapAnnotationParams(annotation: TaxiParser.AnnotationContext): Either<List<CompilationError>, Map<String, Any>> {
      return when {
         annotation.elementValue() != null -> {
            parseElementValue(annotation.elementValue()).map { mapOf("value" to it) }
         }
         annotation.elementValuePairs() != null -> mapElementValuePairs(annotation.elementValuePairs())
         else -> emptyMap<String, Any>().right()// No params specified

      }
   }

   private fun mapElementValuePairs(tokenRule: TaxiParser.ElementValuePairsContext): Either<List<CompilationError>, Map<String, Any>> {
      val pairs = tokenRule.elementValuePair() ?: return emptyMap<String, Any>().right()
      return pairs.map { keyValuePair ->
         parseElementValue(keyValuePair.elementValue()).map { parsedValue ->
            keyValuePair.Identifier().text to parsedValue
         }
      }.invertEitherList().flattenErrors()
         .map { parsedAnnotationPropertyPairs: List<Pair<String, Any>> -> parsedAnnotationPropertyPairs.toMap() }
   }

   private fun parseElementValue(elementValue: TaxiParser.ElementValueContext): Either<List<CompilationError>, Any> {
      return when {
         elementValue.literal() != null -> elementValue.literal().value().right()
         elementValue.qualifiedName() != null -> resolveEnumMember(elementValue.qualifiedName())
         else -> error("Unhandled element value: ${elementValue.text}")
      }
   }

   private fun parseTypeOrVoid(
      namespace: Namespace,
      returnType: TaxiParser.OperationReturnTypeContext?
   ): Either<List<CompilationError>, Type> {
      return if (returnType == null) {
         VoidType.VOID.right()
      } else {
         parseType(namespace, returnType.typeType())
      }
   }

   internal fun parseType(
      namespace: Namespace,
      typeType: TaxiParser.TypeTypeContext
   ): Either<List<CompilationError>, Type> {
      return typeOrError(namespace, typeType).flatMap { type ->
         parseTypeFormat(typeType).flatMap { (formats, zoneOffset) ->
            if (typeType.listType() != null) {
               if (formats.isNotEmpty() || zoneOffset != null) {
                  CompilationError(typeType.start, "It is invalid to declare a format / offset on an array").asList()
                     .left()
               } else {
                  Either.right(ArrayType(type, typeType.toCompilationUnit()))
               }
            } else {
               if (formats.isNotEmpty() || zoneOffset != null) {
                  generateFormattedSubtype(type, FormatsAndZoneoffset(formats, zoneOffset), typeType)
               } else {
                  Either.right(type)
               }
            }
         }
      }
   }

   fun parseAnonymousType(
      namespace: String,
      anonymousTypeCtx: TaxiParser.TypeBodyContext,
      anonymousTypeName: String = AnonymousTypeNameGenerator.generate(),
      anonymousTypeResolutionContext: AnonymousTypeResolutionContext = AnonymousTypeResolutionContext()): Either<List<CompilationError>, Type> {
      compileAnonymousType(namespace, anonymousTypeName, anonymousTypeCtx, anonymousTypeResolutionContext)
      return attemptToLookupTypeByName(namespace, anonymousTypeName, anonymousTypeCtx, SymbolKind.TYPE_OR_MODEL)
         .wrapErrorsInList()
         .flatMap { qualifiedName ->
            typeSystem
               .getTypeOrError(qualifiedName, anonymousTypeCtx)
               .wrapErrorsInList()
         }

   }

   internal fun parseType(
      namespace: Namespace,
      formula: Formula,
      typeType: TaxiParser.TypeTypeContext
   ): Either<List<CompilationError>, Type> {
      val typeOrError = typeOrError(namespace, typeType)
      return typeOrError.flatMap { type ->
         if (typeType.listType() != null) {
            CompilationError(typeType.start, "It is invalid to declare calculated type on an array").asList().left()
         } else {
            generateCalculatedFieldType(type, formula).wrapErrorsInList()
         }
      }
   }

   internal fun typeOrError(
      namespace: Namespace,
      typeType: TaxiParser.TypeTypeContext
   ): Either<List<CompilationError>, Type> {
      return when {
         typeType.aliasedType() != null -> compileInlineTypeAlias(namespace, typeType)
         typeType.classOrInterfaceType() != null -> resolveUserType(
            namespace,
            typeType.classOrInterfaceType(),
            typeType.typeArguments()
         )
//         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text).right()
         else -> throw IllegalArgumentException()
      }
   }

   private fun generateFormattedSubtype(
      type: Type,
      formatOffsetPair: FormatsAndZoneoffset,
      typeType: TaxiParser.TypeTypeContext
   ): Either<List<CompilationError>, Type> {
      val (format, offset) = formatOffsetPair;
      if (offset != null && type.basePrimitive != PrimitiveType.INSTANT) {
         return CompilationError(typeType.start, "@offset is only applicable to Instant based types").asList().left()
      }

      //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets - time offsets range [UTC-12, UTC+14]
      if (offset != null && (offset < -720 || offset > 840)) {
         return CompilationError(
            typeType.start,
            "@offset value can't be larger than 840 (UTC+14) or smaller than -720 (UTC-12)"
         )
            .asList().left()
      }

      val formattedTypeName = QualifiedName.from(type.qualifiedName).let { originalTypeName ->
         val hash = if (offset == null) {
            // just to avoid too many hash changes in existing taxonomies.
            Hashing.sha256().hashString(format.joinToString { it }, Charset.defaultCharset()).toString().takeLast(6)
         } else {
            Hashing.sha256().hashString(format.joinToString { it }.plus(offset.toString()), Charset.defaultCharset())
               .toString().takeLast(6)
         }
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
               format = if (format.isNotEmpty()) format else null,
               offset = offset,
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
         val hash = Hashing.sha256()
            .hashString(operands.sortedBy { it.fullyQualifiedName }.joinToString("_"), Charset.defaultCharset())
            .toString().takeLast(6)
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

   private fun parseTypeFormat(typeType: TaxiParser.TypeTypeContext): Either<List<CompilationError>, FormatsAndZoneoffset> {
      val formatExpressions = typeType
         .parameterConstraint()
         ?.parameterConstraintExpressionList()
         ?.parameterConstraintExpression()
         ?.filter { it.propertyFormatExpression() != null }
         ?.map { it.propertyFormatExpression().StringLiteral() }
         ?.map { stringLiteralValue(it) }
         ?: typeType
            .parameterConstraint()
            ?.temporalFormatList()
            ?.StringLiteral()
            ?.filterNotNull()
            ?.map { stringLiteralValue(it) }
         ?: emptyList()

      val offsetValue = typeType
         .parameterConstraint()?.temporalFormatList()?.instantOffsetExpression()?.intValue()
      return Either.right(FormatsAndZoneoffset(formatExpressions, offsetValue))
   }

   /**
    * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
    * rather than those declared explicitly (type alias PersonFirstName as String)
    */
   private fun compileInlineTypeAlias(
      namespace: Namespace,
      aliasTypeDefinition: TaxiParser.TypeTypeContext
   ): Either<List<CompilationError>, Type> {
      return parseType(namespace, aliasTypeDefinition.aliasedType().typeType()).map { aliasedType ->
         val declaredTypeName = aliasTypeDefinition.classOrInterfaceType().Identifier().text()
         val typeAliasName = if (declaredTypeName.contains(".")) {
            QualifiedNameParser.parse(declaredTypeName)
         } else {
            QualifiedName(namespace, declaredTypeName)
         }
         // Annotations not supported on Inline type aliases
         val annotations = emptyList<Annotation>()
         val typeAlias = TypeAlias(
            typeAliasName.toString(),
            TypeAliasDefinition(aliasedType, annotations, aliasTypeDefinition.toCompilationUnit())
         )
         typeSystem.register(typeAlias)
         typeAlias
      }
   }

   private fun resolveUserType(
      namespace: Namespace,
      requestedTypeName: String,
      imports: List<QualifiedName>,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL
   ): Either<List<CompilationError>, Type> {
      return resolveUserToken(namespace, requestedTypeName, imports, context, symbolKind) { qualifiedTypeName ->
         if (tokens.containsUnparsedType(qualifiedTypeName, symbolKind)) {
            compileToken(qualifiedTypeName)
            typeSystem.getTypeOrError(qualifiedTypeName, context).wrapErrorsInList()
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
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL,
      // Method which takes the resolved qualified name, and checks to see if there is an unparsed token.
      // If so, the method should compile the token and return the compilation result in the either.
      // If not, then the method should return false.
      unparsedCheckAndCompile: (String) -> Either<List<CompilationError>, ImportableToken>?
   ): Either<List<CompilationError>, ImportableToken> {
      return attemptToLookupTypeByName(namespace, requestedTypeName, context, symbolKind)
         .wrapErrorsInList()
         .flatMap { qualifiedTypeName ->
            if (typeSystem.contains(qualifiedTypeName, symbolKind)) {
               return@flatMap typeSystem.getTokenOrError(qualifiedTypeName, context, symbolKind)
                  .wrapErrorsInList()
                  .flatMap { importableToken ->
                     if (importableToken is DefinableToken<*> && !importableToken.isDefined) {
                        unparsedCheckAndCompile(qualifiedTypeName) ?: Either.right(importableToken)
                     } else {
                        Either.right(importableToken)
                     }
                  }
            }

            // Check to see if the token is unparsed, and
            // ccmpile if so
            val compilationResult = unparsedCheckAndCompile(qualifiedTypeName)
            if (compilationResult != null) {
               return@flatMap compilationResult!!
            }

            // Note: Use requestedTypeName, as qualifying it to the local namespace didn't help
            val error = {
               CompilationError(
                  context.start,
                  ErrorMessages.unresolvedType(requestedTypeName),
                  context.source().sourceName
               ).asList()
            }

            if (ArrayType.isArrayTypeName(requestedTypeName)) {
               return@flatMap ArrayType.untyped().right()
            }

            val requestedNameIsQualified = requestedTypeName.contains(".")
            if (!requestedNameIsQualified) {
               val importedTypeName = imports.firstOrNull { it.typeName == requestedTypeName }
               if (importedTypeName != null) {
                  typeSystem.getTokenOrError(importedTypeName.parameterizedName, context).wrapErrorsInList()
               } else {
                  Either.left(error())
               }
            } else {
               Either.left(error())
            }
         }
   }

   private fun resolveUserType(
      namespace: Namespace,
      classType: TaxiParser.ClassOrInterfaceTypeContext,
      typeArgumentCtx: TaxiParser.TypeArgumentsContext? = null,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL
   ): Either<List<CompilationError>, Type> {
      val typeArgumentTokens = typeArgumentCtx?.typeType() ?: emptyList()
      return typeArgumentTokens.map { typeArgument -> parseType(namespace, typeArgument) }
         .invertEitherList().flattenErrors()
         .flatMap { typeArguments ->
            resolveUserType(namespace, classType.Identifier().text(), classType, symbolKind)
               .flatMap { type ->
                  if (typeArgumentCtx == null) {
                     type.right()
                  } else {
                     if (type !is GenericType) {
                        CompilationError(
                           typeArgumentCtx.start,
                           "Type ${type.qualifiedName} does not permit type arguments"
                        ).asList().left()
                     } else {
                        type.withParameters(typeArguments)
                           .mapLeft { listOf(CompilationError(typeArgumentCtx.start, it.message)) }
                     }
                  }
               }
         }
   }

    fun resolveUserType(
      namespace: Namespace,
      requestedTypeName: String,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_MODEL
   ): Either<List<CompilationError>, Type> {
      return resolveUserType(namespace, requestedTypeName, importsInSource(context), context, symbolKind)
   }

   internal fun resolveFunction(
      requestedFunctionName: String,
      context: ParserRuleContext
   ): Either<List<CompilationError>, Function> {
      val namespace = context.findNamespace()
      return resolveUserToken(
         namespace,
         requestedFunctionName,
         importsInSource(context),
         context,
         SymbolKind.FUNCTION
      ) { qualifiedName ->
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


   private fun compileEnum(
      namespace: Namespace,
      typeName: String,
      ctx: TaxiParser.EnumDeclarationContext
   ): Either<List<CompilationError>, EnumType> {
      return compileEnumValues(namespace, typeName, ctx.enumConstants())
         .map { enumValues ->
            val annotations = collateAnnotations(ctx.annotation())
            val basePrimitive = deriveEnumBaseType(enumValues)
            val inherits = parseEnumInheritance(namespace, ctx.enumInheritedType())
            val isLenient = ctx.lenientKeyword() != null
            val enumType = EnumType(
               typeName, EnumDefinition(
               enumValues,
               annotations,
               ctx.toCompilationUnit(),
               inheritsFrom = if (inherits != null) setOf(inherits) else emptySet(),
               typeDoc = parseTypeDoc(ctx.typeDoc()),
               basePrimitive = basePrimitive,
               isLenient = isLenient
            )
            )
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

   private fun compileEnumValues(
      namespace: Namespace,
      enumQualifiedName: String,
      enumConstants: TaxiParser.EnumConstantsContext?
   ): Either<List<CompilationError>, List<EnumValue>> {
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
            parseSynonyms(enumConstant).map { synonyms ->
               synonymRegistry.registerSynonyms(qualifiedName, synonyms, enumConstant)
               EnumValue(
                  name,
                  value,
                  qualifiedName,
                  annotations,
                  synonyms,
                  parseTypeDoc(enumConstant.typeDoc()),
                  isDefault
               )
            }
         }.invertEitherList()
            .mapLeft { listOfLists: List<List<CompilationError>> -> listOfLists.flatten() }
            .flatMap { enumValues -> validateOnlySingleDefaultEnumValuePresent(enumValues, enumConstants) }
      }

   }

   private fun validateOnlySingleDefaultEnumValuePresent(
      enumValues: List<EnumValue>,
      token: TaxiParser.EnumConstantsContext
   ): Either<List<CompilationError>, List<EnumValue>> {
      val defaults = enumValues.filter { it.isDefault }
      if (defaults.size > 1) {
         return Either.left(
            listOf(
               CompilationError(
                  token.start,
                  "Cannot declare multiple default values - found ${defaults.joinToString { it.name }}"
               )
            )
         )
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
   private fun parseSynonyms(enumConstant: TaxiParser.EnumConstantContext): Either<List<CompilationError>, List<EnumValueQualifiedName>> {
      val declaredSynonyms =
         enumConstant.enumSynonymDeclaration()?.enumSynonymSingleDeclaration()?.let { listOf(it.qualifiedName()) }
            ?: enumConstant.enumSynonymDeclaration()?.enumSynonymDeclarationList()?.qualifiedName()
            ?: emptyList()
      return declaredSynonyms.map { synonym ->
         resolveEnumValueName(synonym)

      }.invertEitherList().flattenErrors()
   }

   /**
    * Returns an enum member - requires that the enum has already been compiled.
    * This asserts that both the enum exists, and that it contains the requested member.
    * Use resolveEnumValueName if you need to handle circular references, where the enum may not
    * have already been compiled.
    */
   private fun resolveEnumMember(enumQualifiedNameReference: TaxiParser.QualifiedNameContext): Either<List<CompilationError>, EnumMember> {
      return resolveEnumReference(enumQualifiedNameReference) { enumType, enumValueName ->
         when {
            !enumType.isDefined -> {
               // This happens if there's an enum with a circular reference.
               // That's supported, but we defer
               CompilationError(
                  enumQualifiedNameReference.start,
                  "An internal error occurred processing ${enumType.qualifiedName}, attempting to resolve an EnumMember on a non-compiled enum - use resolveEnumValueName, or compile the enum first."
               ).asList().left()
            }
            !enumType.has(enumValueName) -> CompilationError(
               enumQualifiedNameReference.start,
               "${enumType.qualifiedName} does not have a member $enumValueName"
            ).asList().left()
            else -> Either.right(enumType.member(enumValueName))
         }
      }
   }

   /**
    * Returns an EnumValueQualifiedName.
    * At this point, it is guaranteed that the enum exists, but NOT that the value is present.
    * This is to support use cases where there are circular references (ie., synonyms where two enums point at each other).
    * If you don't need to support that usecase, use resolveEnumMember, which guarantees both the enum and the value.
    */
   private fun resolveEnumValueName(enumQualifiedNameReference: TaxiParser.QualifiedNameContext): Either<List<CompilationError>, EnumValueQualifiedName> {
      return resolveEnumReference(enumQualifiedNameReference) { enumType, enumValueName ->
         Either.right(EnumValue.enumValueQualifiedName(enumType, enumValueName))
      }
   }

   private fun <T> resolveEnumReference(
      enumQualifiedNameReference: TaxiParser.QualifiedNameContext,
      enumSelector: (EnumType, String) -> Either<List<CompilationError>, T>
   ): Either<List<CompilationError>, T> {
      val (enumName, enumValueName) = Enums.splitEnumValueQualifiedName(enumQualifiedNameReference.Identifier().text())

      return resolveUserType(
         enumQualifiedNameReference.findNamespace(),
         enumName.parameterizedName,
         enumQualifiedNameReference
      )
         .flatMap { enumType ->
            if (enumType is EnumType) {
               enumSelector(enumType, enumValueName)
            } else {
               CompilationError(enumQualifiedNameReference.start, "${enumType.qualifiedName} is not an Enum").asList()
                  .left()
            }
         }
   }


   private fun compileEnumExtension(
      namespace: Namespace,
      typeRule: TaxiParser.EnumExtensionDeclarationContext
   ): CompilationError? {
      val enumValues = compileEnumValueExtensions(typeRule.enumConstantExtensions())
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc())

      return attemptToLookupTypeByName(namespace, typeRule.Identifier().text, typeRule)
         .flatMap { typeName ->
            val enum = typeSystem.getType(typeName) as EnumType
            enum.addExtension(EnumExtension(enumValues, annotations, typeRule.toCompilationUnit(), typeDoc = typeDoc))
               .toCompilationError(typeRule.start)
         }.errorOrNull()
   }

   internal fun parseTypeDoc(content: TaxiParser.TypeDocContext?): String? {
      return parseTypeDoc(content?.source()?.content)
   }

   private fun compileFunctions() {
      val compiledFunctions = this.tokens.unparsedFunctions.map { (qualifiedName, namespaceAndParserContext) ->
         compileFunction(namespaceAndParserContext, qualifiedName)
      }.invertEitherList()
         .flattenErrors()
         .collectErrors(errors)
   }

   private fun compileFunction(
      namespaceAndParserContext: Pair<Namespace, TaxiParser.FunctionDeclarationContext>,
      qualifiedName: String
   ): Either<List<CompilationError>, Function> {
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
         }?.reportAndRemoveErrorList(errors) ?: emptyList()

         val function = Function(
            qualifiedName,
            FunctionDefinition(
               parameters, returnType, functionToken.toCompilationUnit()
            )
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
         val members = serviceToken.serviceBody().serviceBodyMember().map { serviceBodyMember ->
            when {
               serviceBodyMember.serviceOperationDeclaration() != null -> compileOperation(serviceBodyMember.serviceOperationDeclaration())
               serviceBodyMember.queryOperationDeclaration() != null -> compileQueryOperation(serviceBodyMember.queryOperationDeclaration())
               else -> error("Unhandled type of service member. ")
            }
         }
            .reportAndRemoveErrorList(errors)

         Service(
            qualifiedName,
            members,
            collateAnnotations(serviceToken.annotation()),
            listOf(serviceToken.toCompilationUnit()),
            serviceDoc
         )
      }
      this.services.addAll(services)
   }

   private fun compileQueryOperation(queryOperation: TaxiParser.QueryOperationDeclarationContext): Either<List<CompilationError>, QueryOperation> {
      val namespace = queryOperation.findNamespace()
      return parseType(namespace, queryOperation.typeType())
         .flatMap { returnType ->
            parseCapabilities(queryOperation).map { capabilities ->
               val name = queryOperation.Identifier().text
               val grammar = queryOperation.queryGrammarName().Identifier().text
               val operationParameters =
                  queryOperation.operationParameterList().operationParameter().map { operationParameterContext ->
                     parseParameter(namespace, operationParameterContext)
                  }.reportAndRemoveErrorList(errors)
               QueryOperation(
                  name = name,
                  annotations = collateAnnotations(queryOperation.annotation()),
                  grammar = grammar,
                  returnType = returnType,
                  compilationUnits = listOf(queryOperation.toCompilationUnit()),
                  typeDoc = parseTypeDoc(queryOperation.typeDoc()),
                  capabilities = capabilities,
                  parameters = operationParameters
               )
            }

         }
   }

   private fun parseCapabilities(queryOperation: TaxiParser.QueryOperationDeclarationContext): Either<List<CompilationError>, List<QueryOperationCapability>> {
      return queryOperation.queryOperationCapabilities().queryOperationCapability().map { capabilityContext ->
         when {
            capabilityContext.queryFilterCapability() != null -> {
               val filterOperations =
                  capabilityContext.queryFilterCapability().filterCapability().map { filterCapability ->
                     Operator.parse(filterCapability.text)
                  }
               FilterCapability(filterOperations).right()
            }
            else -> {
               try {
                  SimpleQueryCapability.parse(capabilityContext.text).right()
               } catch (e: Exception) {
                  // Have hard-coded filter into the error message here, as it's not handled by the enum.  Probably gonna bite us at some point...
                  CompilationError(
                     queryOperation.start,
                     "Unable to parse '${capabilityContext.text}' to a query capability.  Expected one of filter, ${
                        SimpleQueryCapability.values().joinToString { it.symbol }
                     }"
                  ).left()
               }
            }
         }
      }.invertEitherList()
   }

   private fun compileOperation(operationDeclaration: TaxiParser.ServiceOperationDeclarationContext): Either<List<CompilationError>, Operation> {
      val signature = operationDeclaration.operationSignature()
      val namespace = operationDeclaration.findNamespace()
      return parseTypeOrVoid(namespace, signature.operationReturnType())
         .flatMap { returnType ->
            val scope = operationDeclaration.operationScope()?.Identifier()?.text
            val operationParameters = signature.parameters().map { operationParameterContext ->
               parseParameter(namespace, operationParameterContext)
            }.reportAndRemoveErrorList(errors)

            parseOperationContract(operationDeclaration, returnType, namespace).map { contract ->
               Operation(
                  name = signature.Identifier().text,
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
   }

   private fun parseParameter(
      namespace: Namespace,
      operationParameterContext: TaxiParser.OperationParameterContext
   ): Either<List<CompilationError>, Parameter> {
      return parseType(namespace, operationParameterContext.typeType())
         .flatMap { paramType ->
            mapConstraints(operationParameterContext.typeType(), paramType, namespace).map { constraints ->
               val isVarargs = operationParameterContext.varargMarker() != null
               Parameter(
                  collateAnnotations(operationParameterContext.annotation()), paramType,
                  name = operationParameterContext.parameterName()?.Identifier()?.text,
                  constraints = constraints,
                  isVarArg = isVarargs
               )
            }
         }
   }

   private fun parseOperationContract(
      operationDeclaration: TaxiParser.ServiceOperationDeclarationContext,
      returnType: Type,
      namespace: Namespace
   ): Either<List<CompilationError>, OperationContract?> {
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

   internal fun mapConstraints(
      typeType: TaxiParser.TypeTypeContext,
      paramType: Type,
      namespace: Namespace
   ): Either<List<CompilationError>, List<Constraint>> {
      if (typeType.parameterConstraint() == null) {
         return Either.right(emptyList())
      }
      return OperationConstraintConverter(
         typeType.parameterConstraint()
            .parameterConstraintExpressionList(),
         paramType, typeResolver(namespace)
      ).constraints()
   }

   private fun compilePolicies() {
      this.tokens.unparsedPolicies.map { (name, namespaceTokenPair) ->
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
      }.invertEitherList().flattenErrors()
         .mapLeft { this.errors.addAll(it) }
         .map { this.policies.addAll(it) }
   }

   fun typeResolver(namespace: Namespace): NamespaceQualifiedTypeResolver {
      return object : NamespaceQualifiedTypeResolver {
         override val namespace: String = namespace

         override fun resolve(context: TaxiParser.TypeTypeContext): Either<List<CompilationError>, Type> {
            return parseType(namespace, context)
         }

         override fun resolve(
            requestedTypeName: String,
            context: ParserRuleContext
         ): Either<List<CompilationError>, Type> {
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
         listOf(
            PolicyStatement(
               ElseCondition(),
               Instructions.parse(token.policyInstruction()),
               token.toCompilationUnit()
            )
         )
      }
      return RuleSet(scope, statements)
   }

   private fun compilePolicyStatement(namespace: String, token: TaxiParser.PolicyStatementContext): PolicyStatement {
      val (condition, instruction) = compileCondition(namespace, token)
      return PolicyStatement(condition, instruction, token.toCompilationUnit())
   }

   private fun compileCondition(
      namespace: String,
      token: TaxiParser.PolicyStatementContext
   ): Pair<Condition, Instruction> {
      return when {
         token.policyCase() != null -> compileCaseCondition(namespace, token.policyCase())
         token.policyElse() != null -> ElseCondition() to Instructions.parse(token.policyElse().policyInstruction())
         else -> error("Invalid condition is neither a case nor an else")
      }
   }

   private fun compileCaseCondition(
      namespace: String,
      case: TaxiParser.PolicyCaseContext
   ): Pair<Condition, Instruction> {
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

fun <T> Either<List<CompilationError>, T>.collectErrors(errorCollection: MutableList<CompilationError>): Either<List<ReportedError>, T> {
   return this.mapLeft { errors ->
      errors.map { error ->
         errorCollection.add(error)
         ReportedError(error)
      }
   }
}

fun <T> Either<CompilationError, T>.collectError(errors: MutableList<CompilationError>): Either<ReportedError, T> {
   return this.mapLeft { error ->
      errors.add(error)
      ReportedError(error)
   }
}

fun <T : Any> List<Either<List<CompilationError>, T>>.reportAndRemoveErrorList(errorCollection: MutableList<CompilationError>): List<T> {
   return this.mapNotNull { item ->
      item.getOrHandle { errors ->
         errorCollection.addAll(errors)
         null
      }
   }
}

fun <T : Any> List<Either<CompilationError, T>>.reportAndRemoveErrors(errorCollection: MutableList<CompilationError>): List<T> {
   return this.mapNotNull { it.reportIfCompilationError(errorCollection) }
}

private fun <T : Any> Either<CompilationError, T>.reportIfCompilationError(errorCollection: MutableList<CompilationError>): T? {
   return this.getOrHandle { compilationError ->
      errorCollection.add(compilationError)
      null
   }
}

// Wrapper class to indicate that an underlying error has been captured, but handled
// This is primarily to stop us processing errors multiple times as they make their way
// up the stack
data class ReportedError(val error: CompilationError)
data class FormatsAndZoneoffset(val formats: List<String>, val utcZoneoffsetInMinutes: Int?)

fun CompilationError.asList(): List<CompilationError> = listOf(this)

enum class SymbolKind {
   TYPE_OR_MODEL,
   ANNOTATION,
   FUNCTION;

   fun matches(token: ParserRuleContext): Boolean {
      return when (this) {
//         MATCH_ANYTHING -> true
         ANNOTATION -> {
            token is TaxiParser.AnnotationTypeDeclarationContext
         }
         TYPE_OR_MODEL -> {
            when (token) {
               is TaxiParser.AnnotationTypeDeclarationContext -> false
               is TaxiParser.TypeDeclarationContext -> true
               is TaxiParser.EnumDeclarationContext -> true
               is TaxiParser.TypeAliasDeclarationContext -> true
               is TaxiParser.TypeTypeContext -> true
               else -> {
                  TODO()
               }
            }
         }
         else -> {
            TODO("Matching on token type against symbol kind ${this.name} is not implemented.  Note - got passed a token of ${token::class.simpleName}")
         }
      }
   }

   fun matches(token: ImportableToken): Boolean {
      return when (this) {
//         MATCH_ANYTHING -> true
         TYPE_OR_MODEL -> token is PrimitiveType || (token is UserType<*, *> && token !is AnnotationType)
         FUNCTION -> token is Function
         ANNOTATION -> token is AnnotationType
      }
   }
}

object AnonymousTypeNameGenerator {
   private val random: SecureRandom = SecureRandom()
   private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

   // This is both shorter than a UUID (e.g. Xl3S2itovd5CDS7cKSNvml4_ODA)  and also more secure having 160 bits of entropy.
   fun generate(): String {
      val buffer = ByteArray(20)
      random.nextBytes(buffer)
      return "AnonymousProjectedType${encoder.encodeToString(buffer)}"
   }
}
