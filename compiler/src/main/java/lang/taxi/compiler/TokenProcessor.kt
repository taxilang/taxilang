package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.handleErrorWith
import arrow.core.left
import arrow.core.right
import lang.taxi.*
import lang.taxi.TaxiParser.*
import lang.taxi.accessors.Argument
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.annotations.AnnotationTypeBodyContent
import lang.taxi.compiler.fields.FieldCompiler
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.compiler.fields.TypeBodyContext
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.toExpressionGroup
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionDefinition
import lang.taxi.functions.FunctionModifiers
import lang.taxi.linter.Linter
import lang.taxi.policies.*
import lang.taxi.query.FactValue
import lang.taxi.query.Parameter
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.*
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintValidator
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.services.operations.constraints.OperationConstraintConverter
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.flatten
import kotlin.collections.set

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

   constructor(
      tokens: Tokens,
      collectImports: Boolean,
      typeChecker: TypeChecker,
      linter: Linter = Linter.empty()
   ) : this(
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

   val errors = mutableListOf<CompilationError>()

//   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)
//   private val calculatedFieldSetProcessor = CalculatedFieldSetProcessor(this)

   private val tokensCurrentlyCompiling = mutableSetOf<String>()
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
      return errors to TaxiDocument(
         types,
         services.toSet(),
         policies.toSet(),
         functions.toSet(),
         annotations.toSet(),
         views.toSet(),
         queries.toSet()
      )
   }

   fun buildQueries(): Pair<List<CompilationError>, List<TaxiQlQuery>> {
      compile()
      return errors to queries
   }

   // Primarily for language server tooling, rather than
   // compile time - though it should be safe to use in all scnearios
   fun lookupSymbolByName(contextRule: TypeReferenceContext): String {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return lookupSymbolByName(namespace, contextRule)
   }

   fun lookupSymbolByName(text: String, contextRule: ParserRuleContext): Either<List<CompilationError>, String> {
      createEmptyTypes()
      val namespace = contextRule.findNamespace()
      return attemptToLookupSymbolByName(namespace, text, contextRule).wrapErrorsInList()
   }

   fun findDeclaredServiceNames(): List<QualifiedName> {
      return tokens.unparsedServices.map { (name, _) ->
         QualifiedName.from(name)
      }
   }

   fun findDeclaredTypeNames(): List<QualifiedName> {
      createEmptyTypes()

      // We need to check all the ObjectTypes, to see if they declare any inline type aliases
      val inlineTypeAliases = tokens.unparsedTypes.toList().filter { (_, tokenPair) ->
         val (_, ctx) = tokenPair
         ctx is TypeDeclarationContext
      }.flatMap { (_, tokenPair) ->
         val (namespace, ctx) = tokenPair
         val typeCtx = ctx as TypeDeclarationContext
         val typeAliasNames = typeCtx.typeBody()?.typeMemberDeclaration()
            ?.filter { memberDeclaration ->
               memberDeclaration.exception == null
                  // When compiling partial sources in the editors, the field declaration can be
                  // null at this point.  It's invalid, but we don't want to throw an NPE
                  && memberDeclaration.fieldDeclaration() != null
            }
            ?.mapNotNull { memberDeclaration ->
               val fieldTypeDeclaration = memberDeclaration.fieldDeclaration().fieldTypeDeclaration()
               when {
                  fieldTypeDeclaration == null -> null
                  fieldTypeDeclaration.aliasedType() != null -> lookupSymbolByName(
                     namespace,
                     memberDeclaration.fieldDeclaration().fieldTypeDeclaration().nullableTypeReference().typeReference()
                  )

                  fieldTypeDeclaration.inlineInheritedType() != null -> lookupSymbolByName(
                     namespace,
                     memberDeclaration.fieldDeclaration().fieldTypeDeclaration().nullableTypeReference().typeReference()
                  )

                  else -> null
               }
            } ?: emptyList()
         typeAliasNames.map { QualifiedName.from(it) }
      }

      val declaredTypeNames = typeSystem.typeList().map { it.toQualifiedName() }
      return declaredTypeNames + inlineTypeAliases
   }

   private fun lookupSymbolByName(namespace: Namespace, type: TypeReferenceContext): String {
      return if (PrimitiveType.isPrimitiveType(type.text)) {
         PrimitiveType.fromDeclaration(type.text).qualifiedName
      } else {
         lookupSymbolByName(namespace, type.qualifiedName().text, importsInSource(type))
      }
   }

   internal fun attemptToLookupSymbolByName(
      namespace: Namespace,
      name: String,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE
   ): Either<CompilationError, String> {
      return try {
         lookupSymbolByName(namespace, name, importsInSource(context), symbolKind).right()
      } catch (e: AmbiguousNameException) {
         CompilationError(context.start, e.message!!, context.source().normalizedSourceName).left()
      }
   }

   // THe whole additionalImports thing is for when we're
   // accessing prior to compiling (ie., in the language server).
   // During normal compilation, don't need to pass anything
   @Deprecated("call attemptToQualify, so errors are caught property")
   private fun lookupSymbolByName(
      namespace: Namespace,
      name: String,
      importsInSource: List<QualifiedName>,
      symbolKind: SymbolKind = SymbolKind.TYPE
   ): String {
      return typeSystem.qualify(namespace, name, importsInSource, symbolKind)

   }

   internal fun getType(
      namespace: Namespace,
      name: String,
      context: ParserRuleContext
   ): Either<List<CompilationError>, Type> {
      return attemptToLookupSymbolByName(namespace, name, context).map { qfn ->
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
//      validateFormulas()
//      validaCaseWhenLogicalExpressions()

      // Queries
      compileQueries()
      //
   }

   private fun compileQueries() {
      this.tokens.anonymousQueries.forEach { (namespace, anonymousQueryContex) ->
         val queryName = QualifiedName(namespace, NameGenerator.generate("AnonymousQuery"))
         QueryCompiler(this, expressionCompiler())
            .parseQueryBody(
               name = queryName,
               parameters = emptyList(),
               annotations = collateAnnotations(anonymousQueryContex.queryBody().annotation()),
               docs = null,
               ctx = anonymousQueryContex.queryBody(),
               compilationUnit = anonymousQueryContex.toCompilationUnit(includeImportsPresentInFile = true)
            )
            .mapLeft { compilationErrors -> errors.addAll(compilationErrors) }
            .map { taxiQlQuery ->
               queries.add(taxiQlQuery)
            }
      }

      this.tokens.namedQueries.forEach { (namespace, namedQueryContext) ->
         val queryName = namedQueryContext.queryName().identifier().text
         val queryQualifiedName = QualifiedName(namespace, queryName)
         val annotations = collateAnnotations(namedQueryContext.annotation())
         val docs = parseTypeDoc(namedQueryContext.typeDoc())
         val parametersOrErrors =
            namedQueryContext.queryName().queryParameters()?.queryParamList()?.queryParam()
               ?.mapIndexed { idx, queryParam ->
                  val annotations = collateAnnotations(queryParam.annotation())
                  val parameterName = queryParam.identifier().text ?: "p$idx"
                  val queryParameter: Either<List<CompilationError>, Parameter> =
                     typeOrError(namedQueryContext.findNamespace(), queryParam.typeReference()).map { parameterType ->
                        Parameter(parameterName, FactValue.Variable(parameterType, parameterName), annotations)
                     }
                  queryParameter
               }?.invertEitherList() ?: emptyList<Parameter>().right()
         parametersOrErrors
            .mapLeft { compilationErrors -> errors.addAll(compilationErrors.flatten()) }
            .map { parameters ->
               QueryCompiler(this, expressionCompiler())
                  .parseQueryBody(
                     name = queryQualifiedName,
                     parameters = parameters,
                     annotations = annotations,
                     docs = docs,
                     ctx = namedQueryContext.queryBody(),
                     compilationUnit = namedQueryContext.toCompilationUnit(includeImportsPresentInFile = true)
                  )
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
                        enum.ofName(enumValueName).synonyms.none { it == synonymEnumValue } // Ignore synonyms that are already present
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
//
//
//   private fun validaCaseWhenLogicalExpressions() {
//      typeSystem.typeList().filterIsInstance<ObjectType>()
//         .forEach { type ->
//            type
//               .allFields
//               .filter {
//                  it.accessor is ConditionalAccessor &&
//                     (it.accessor as ConditionalAccessor).expression is WhenExpression
//               }
//               .forEach {
//                  val whenExpression = ((it.accessor as ConditionalAccessor).expression as WhenExpression)
//                  val logicalExpressions = whenExpression
//                     .cases.map { aCase -> aCase.matchExpression }
//                     .filterIsInstance<LogicalExpression>()
//                  when {
////                     logicalExpressions.isNotEmpty() && whenFieldSetCondition.selectorExpression !is EmptyReferenceSelector -> {
////                        errors.add(
////                           CompilationError(
////                              type,
////                              "when case for ${it.name} in ${type.qualifiedName} cannot have reference selector use when { .. } syntax"
////                           )
////                        )
////                     }
////                     whenFieldSetCondition.selectorExpression is EmptyReferenceSelector &&
////                        whenFieldSetCondition.cases.map { it.matchExpression }.filter { it !is ElseMatchExpression }
////                           .any { it !is LogicalExpression } -> {
////                        errors.add(
////                           CompilationError(
////                              type,
////                              "when case for ${it.name} in ${type.qualifiedName} can only logical expression when cases"
////                           )
////                        )
////                     }
//                     else -> validateLogicalExpression(type, typeSystem, it, logicalExpressions)
//                  }
//               }
//         }
//   }
//
//   private fun validateLogicalExpression(
//      type: ObjectType,
//      typeSystem: TypeSystem,
//      field: Field,
//      logicalExpressions: List<LogicalExpression>
//   ) {
//      logicalExpressions.forEach {
//         when (it) {
//            is ComparisonExpression -> validateComparisonExpression(it, type)
//            is AndExpression -> validateLogicalExpression(type, typeSystem, field, listOf(it.left, it.right))
//            is OrExpression -> validateLogicalExpression(type, typeSystem, field, listOf(it.left, it.right))
//         }
//      }
//   }
//
//   private fun validateComparisonExpression(comparisonExpression: ComparisonExpression, type: ObjectType) {
//      val right = comparisonExpression.right
//      val left = comparisonExpression.left
//      when {
//         right is FieldReferenceEntity && left is FieldReferenceEntity -> {
//            validateFieldReferenceEntity(right, type)
//            validateFieldReferenceEntity(left, type)
//         }
//
//         right is ConstantEntity && left is FieldReferenceEntity -> {
//            val leftField = validateFieldReferenceEntity(left, type)
//            validateConstantEntityAgainstField(leftField, right, type, comparisonExpression.operator)
//         }
//
//         right is FieldReferenceEntity && left is ConstantEntity -> {
//            val rightField = validateFieldReferenceEntity(right, type)
//            validateConstantEntityAgainstField(rightField, left, type, comparisonExpression.operator)
//         }
//      }
//   }
//
//   private fun validateFieldReferenceEntity(fieldReferenceEntity: FieldReferenceEntity, type: ObjectType): Field? {
//      val referencedField = type.allFields.firstOrNull { field -> field.name == fieldReferenceEntity.fieldName }
//      if (referencedField == null) {
//         errors.add(CompilationError(type, "${fieldReferenceEntity.fieldName} is not a field of ${type.qualifiedName}"))
//      } else {
//         if (referencedField.type.basePrimitive == null) {
//            errors.add(
//               CompilationError(
//                  type,
//                  "${fieldReferenceEntity.fieldName} is not a field of ${type.qualifiedName}"
//               )
//            )
//         }
//      }
//      return referencedField
//   }
//
//   private fun validateConstantEntityAgainstField(
//      field: Field?,
//      constantEntity: ConstantEntity,
//      type: ObjectType,
//      operator: ComparisonOperator
//   ) {
//      if (field?.type?.basePrimitive != PrimitiveType.DECIMAL &&
//         field?.type?.basePrimitive != PrimitiveType.INTEGER &&
//         field?.type?.basePrimitive != PrimitiveType.STRING
//      ) {
//         errors.add(
//            CompilationError(
//               type,
//               "${field?.name} should be a String, Int or Decimal based field of ${type.qualifiedName}"
//            )
//         )
//      }
//      if (constantEntity.value is String && field?.type?.basePrimitive != PrimitiveType.STRING) {
//         errors.add(CompilationError(type, "${field?.name} is not a String based field of ${type.qualifiedName}"))
//      }
//
//      if (constantEntity.value is Number && (field?.type?.basePrimitive != PrimitiveType.DECIMAL && field?.type?.basePrimitive != PrimitiveType.INTEGER)) {
//         errors.add(CompilationError(type, "${field?.name} is not a numeric based field of ${type.qualifiedName}"))
//      }
//
//      if (!operator.applicablePrimitives.contains(field?.type?.basePrimitive)) {
//         errors.add(
//            CompilationError(
//               type,
//               "${operator.symbol} is not applicable to ${field?.name} field of ${type.qualifiedName}"
//            )
//         )
//
//      }
//   }


   private fun createEmptyTypes() {
      if (createEmptyTypesPerformed) {
         return
      }
      tokens.unparsedFunctions.forEach { tokenName, (_, token) ->
         typeSystem.register(Function.undefined(tokenName))
      }
      tokens.unparsedTypes.forEach { tokenName, (_, token) ->
         when (token) {
            is AnnotationTypeDeclarationContext -> typeSystem.register(AnnotationType.undefined(tokenName))
            is EnumDeclarationContext -> typeSystem.register(EnumType.undefined(tokenName))
            is TypeDeclarationContext -> typeSystem.register(ObjectType.undefined(tokenName))
            is TypeAliasDeclarationContext -> typeSystem.register(TypeAlias.undefined(tokenName))
         }
      }
      val serviceDefinitions = tokens.unparsedServices.map { entry ->
         val qualifiedName = entry.key
         val serviceBody = entry.value.second.serviceBody()
         val operations = serviceBody?.serviceBodyMember()
            ?.mapNotNull { it.serviceOperationDeclaration() }
            ?.map { operationDeclaration -> operationDeclaration.operationSignature().identifier().text }
            ?: emptyList()
         ServiceDefinition(qualifiedName, operations)
      }
      typeSystem.registerServiceDefinitions(serviceDefinitions)
      createEmptyTypesPerformed = true
   }

   private fun compileTokens() {
      val enumUnparsedTypes = tokens
         .unparsedTypes
         .filter { it.value.second is EnumDeclarationContext }

      val nonEnumParsedTypes = tokens
         .unparsedTypes
         .filter { it.value.second !is EnumDeclarationContext }

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
         when (tokenRule) {
            is TypeDeclarationContext -> compileType(namespace, tokenName, tokenRule).collectErrors(errors)
            is AnnotationTypeDeclarationContext -> compileAnnotationType(
               tokenName,
               namespace,
               tokenRule
            ).collectErrors(errors)

            is EnumDeclarationContext -> compileEnum(namespace, tokenName, tokenRule).collectErrors(errors)
            is TypeAliasDeclarationContext -> compileTypeAlias(
               namespace,
               tokenName,
               tokenRule
            ).collectErrors(errors)
            // TODO : This is a bit broad - assuming that all typeType's that hit this
            // line will be a TypeAlias inline.  It could be a normal field declaration.
            is FieldTypeDeclarationContext -> compileInlineTypeAlias(namespace, tokenRule).collectErrors(
               errors
            )

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
            is TypeExtensionDeclarationContext -> compileTypeExtension(namespace, typeRule)
            is TypeAliasExtensionDeclarationContext -> compileTypeAliasExtension(namespace, typeRule)
            is EnumExtensionDeclarationContext -> compileEnumExtension(namespace, typeRule)
            else -> TODO("Not handled: $typeRule")
         }
      }
      this.errors.addAll(errors)

   }

   private fun compileTypeAliasExtension(
      namespace: Namespace,
      typeRule: TypeAliasExtensionDeclarationContext
   ): CompilationError? {
      return attemptToLookupSymbolByName(namespace, typeRule.identifier().text, typeRule).flatMap { typeName ->
         val type = typeSystem.getType(typeName) as TypeAlias
         val annotations = collateAnnotations(typeRule.annotation())
         val typeDoc = parseTypeDoc(typeRule.typeDoc())
         type.addExtension(TypeAliasExtension(annotations, typeRule.toCompilationUnit(), typeDoc))
            .mapLeft { it.toCompilationError(typeRule.start) }
      }.errorOrNull()
   }

   private fun compileTypeExtension(
      namespace: Namespace,
      typeRule: TypeExtensionDeclarationContext
   ): CompilationError? {
      val typeName =
         when (val typeNameEither = attemptToLookupSymbolByName(namespace, typeRule.identifier().text, typeRule)) {
            is Either.Left -> return typeNameEither.value // return the compilation error now and stop
            is Either.Right -> typeNameEither.value
         }
      val type = typeSystem.getType(typeName)

      return when (type) {
         is ObjectType -> compileObjectTypeExtension(namespace, typeName, type, typeRule)
         else -> CompilationError(
            typeRule.toCompilationUnit(),
            "Defining type extensions for token type ${type::class.simpleName} is not supported"
         )
      }

   }


   private fun compileObjectTypeExtension(
      namespace: Namespace,
      typeName: String,
      type: ObjectType,
      typeRule: TypeExtensionDeclarationContext
   ): CompilationError? {
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc()?.source()?.content)
      val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
         val fieldName = member.typeExtensionFieldDeclaration().identifier().text
         val fieldAnnotations = collateAnnotations(member.annotation())
         val refinedType =
            member.typeExtensionFieldDeclaration()?.typeExtensionFieldTypeRefinement()?.typeReference()?.let {
               val refinedType = typeSystem.getType(lookupSymbolByName(namespace, it.text, importsInSource(it)))
               assertTypesCompatible(type.field(fieldName).type, refinedType, fieldName, typeName, typeRule)
            }

//         val enumConstantValue = member
//            ?.typeExtensionFieldDeclaration()
//            ?.typeExtensionFieldTypeRefinement()
//            ?.constantDeclaration()
//            ?.defaultDefinition()
//            ?.qualifiedName()?.let { enumDefaultValue ->
//               assertEnumDefaultValueCompatibility(
//                  refinedType!! as EnumType,
//                  enumDefaultValue.text,
//                  fieldName,
//                  typeRule
//               )
//            }

//         val constantValue = enumConstantValue ?: member
//            ?.typeExtensionFieldDeclaration()
//            ?.typeExtensionFieldTypeRefinement()
//            ?.constantDeclaration()
//            ?.defaultDefinition()
//            ?.let { defaultDefinitionContext ->
//               defaultValueParser.parseDefaultValue(defaultDefinitionContext, refinedType!!)
//            }?.collectError(errors)?.getOrElse { null }

         FieldExtension(fieldName, fieldAnnotations, refinedType, null)
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
      typeRule: TypeExtensionDeclarationContext
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
      typeRule: TypeExtensionDeclarationContext
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
      typeRule: TypeExtensionDeclarationContext
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
      tokenRule: TypeAliasDeclarationContext
   ): Either<List<CompilationError>, TypeAlias> {
      return parseType(namespace, tokenRule.aliasedType().typeReference()).map { aliasedType ->
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

   fun List<TerminalNode>.text(): String {
      return this.joinToString(".")
   }

   private fun compileAnnotationType(
      name: String,
      namespace: Namespace,
      token: AnnotationTypeDeclarationContext
   ): Either<List<CompilationError>, AnnotationType> {
      val typeWithFields = AnnotationTypeBodyContent(token.annotationTypeBody(), namespace)
      val fieldCompiler = FieldCompiler(
         this,
         typeWithFields,
         name,
         this.errors
      )
      val fieldsOrErrors = fieldCompiler
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
         }.invertEitherList()

      return fieldsOrErrors.map { fields ->
         val annotations = collateAnnotations(token.annotation())
         val typeDoc = parseTypeDoc(token.typeDoc())
         val definition = AnnotationTypeDefinition(
            fields,
            annotations,
            typeDoc,
            token.toCompilationUnit()
         )
         typeSystem.register(
            AnnotationType(
               name,
               definition
            )
         )
      }

   }

   private fun compileType(
      namespace: Namespace,
      typeName: String,
      ctx: TypeDeclarationContext,
      activeScopes: List<ProjectionFunctionScope> = emptyList()
   ): Either<List<CompilationError>, ObjectType> {
      val typeKind = TypeKind.fromSymbol(ctx.typeKind().text)
      if (ctx.typeBody()?.spreadOperatorDeclaration() != null) {
         return listOf(
            CompilationError(
               ctx.start,
               "Spread operator is not allowed for model definitions. Found in model ${ctx.typeBody()?.text}",
               ctx.source().normalizedSourceName
            )
         )
            .left()
      }
      val fields = ctx.typeBody()?.let { typeBody ->
         val typeBodyContext = TypeBodyContext(typeBody, namespace)
         FieldCompiler(this, typeBodyContext, typeName, this.errors)
            .compileAllFields()
      } ?: emptyList()

      val annotations = collateAnnotations(ctx.annotation())
      val modifiers = parseModifiers(ctx.typeModifier())
      val declaredInheritence = parseTypeInheritance(namespace, ctx.listOfInheritedTypes())

      val interimDefinition = ObjectTypeDefinition(
         fields = fields.toSet(),
         annotations = annotations.toSet(),
         modifiers = modifiers,
         inheritsFrom = declaredInheritence,
         formatAndOffset = null,
         typeDoc = "This type is currently under construction",
         typeKind = typeKind,
         expression = null, // We'll parse the expression in a bit...
         compilationUnit = ctx.toCompilationUnit()
      )
      val interimType = ObjectType(
         typeName, interimDefinition
      )
      // Before we can parse the expression, we need to register an interim version of this type,
      // to handle things like circular references within the expression.
      // TODO : Passing overwrite here, as otherwsie we're getting redefinition exceptions.
      // However, that shouldn't happen, as this should be the first time we register a type.
      // Need to investigate.
      if (!this.typeSystem.isDefined(typeName)) {
         this.typeSystem.register(interimType)
      }


      val expression = ctx.expressionTypeDeclaration()?.let {
         it.expressionGroup().map { expressionGroup -> parseTypeExpression(expressionGroup, activeScopes) }
      }?.invertEitherList()
         ?.flattenErrors()
         ?.map { it.toExpressionGroup() }
         ?.getOrElse { errors ->
            this.errors.addAll(errors)
            null
         }

      val inherits = declaredInheritence.let { explicitInheritence ->
         // If we have an expression, then the return type is inferrable from that
         if (explicitInheritence.isEmpty() && expression != null) {
            setOf(expression.returnType)
         } else {
            explicitInheritence
         }

      }

      checkForCircularTypeInheritance(typeName, ctx, inherits)?.let { compilationError ->
         return listOf(compilationError).left()
      }

      val typeDoc = parseTypeDoc(ctx.typeDoc()?.source()?.content)
      val dependantTypeNames = fields.map { it.type.toQualifiedName() } +
         annotations.mapNotNull { it.type?.toQualifiedName() } +
         inherits
            // Exclude imports of formatted types.
            // This happens in cases like:
            // import lang.taxi.FormattedInstant_428af4 <-- We want to exclude this import, it's not useful
            // type FooDate inherits Instant( @format = 'mm/dd/yyThh:nn:ss.mmmmZ' )
            .filterNot { it.declaresFormat && it.toQualifiedName().typeName.startsWith("Formatted") }
            .map { it.toQualifiedName() }


      val definition = ObjectTypeDefinition(
         fields = fields.toSet(),
         annotations = annotations.toSet(),
         modifiers = modifiers,
         inheritsFrom = inherits,
         formatAndOffset = null,
         typeDoc = typeDoc,
         typeKind = typeKind,
         expression = expression,
         compilationUnit = ctx.toCompilationUnit(dependantTypeNames)
      )
      val type = ObjectType(
         typeName, definition
      ).let { typeWithoutFormat ->
         val format = parseTypeFormat(annotations, typeWithoutFormat, ctx)
            .getOrElse {
               this.errors.addAll(it)
               null
            }
         ObjectType(
            typeName,
            definition.copy(formatAndOffset = format)
         )
      }

      return this.typeSystem.register(
         type, overwrite = true
      ).right()
   }

   fun expressionCompiler(
      fieldCompiler: FieldCompiler? = null,
      scopedArguments: List<Argument> = emptyList()
   ): ExpressionCompiler {
      return ExpressionCompiler(this, typeChecker, errors, fieldCompiler, scopedArguments)
   }

   private fun parseTypeExpression(
      expressionGroup: ExpressionGroupContext,
      activeScopes: List<ProjectionFunctionScope>
   ): Either<List<CompilationError>, Expression> {
      return expressionCompiler(scopedArguments = activeScopes).compile(expressionGroup)
   }

   private fun checkForCircularTypeInheritance(
      typeName: String,
      ctx: TypeDeclarationContext,
      inherits: Set<Type>,
      detectedTypeNames: MutableSet<String> = mutableSetOf()
   ): CompilationError? {
      if (inherits.isEmpty()) {
         return null
      }
      val inheritsFromTypeNames = inherits.map { it.qualifiedName }.toSet()
      val typesToCheck = inherits.filterNot { detectedTypeNames.contains(it.qualifiedName) }
         .filter { it !is PrimitiveType }

      // Does this directly inherit from itself?
      if (inheritsFromTypeNames.contains(typeName)) {
         return CompilationError(
            ctx.toCompilationUnit(),
            "$typeName cannot inherit from itself"
         )
      }

      // Is there a loop somewhere?
      val loopTypeNames = inheritsFromTypeNames.filter { detectedTypeNames.contains(it) }
      if (loopTypeNames.isNotEmpty()) {
         return CompilationError(
            ctx.toCompilationUnit(),
            "$typeName contains a loop in it's inheritance.  Check the inheritance of the following types: ${
               loopTypeNames.filter { it != typeName }.joinToString(", ")
            }"
         )
      }
      detectedTypeNames.add(typeName)
      return typesToCheck
         .asSequence()
         .mapNotNull {
            checkForCircularTypeInheritance(it.qualifiedName, ctx, it.inheritsFrom, detectedTypeNames)
         }
         .firstOrNull()
   }

   private fun compileAnonymousType(
      namespace: Namespace,
      typeName: String,
      anonymousTypeDefinition: AnonymousTypeDefinitionContext,
      resolutionContext: ResolutionContext,
   ): Either<List<CompilationError>, ObjectType> {
      val annotations = collateAnnotations(anonymousTypeDefinition.annotation())
      val (fields, expression) = anonymousTypeDefinition.typeBody().let { typeBody ->
         val typeBodyFieldDeclarationParent =
            typeBody.parent?.searchUpForRule(FieldDeclarationContext::class.java) as? FieldDeclarationContext
         val typeBodyFieldObjectType =
            typeBodyFieldDeclarationParent?.fieldTypeDeclaration()?.nullableTypeReference()?.typeReference()?.let {
               parseType(namespace, it).getOrNull() as? ObjectType
            }
         val typeBodyContext = TypeBodyContext(typeBody, namespace, typeBodyFieldObjectType)
         val fieldCompiler = FieldCompiler(this, typeBodyContext, typeName, errors, resolutionContext)
         val compiledFields = fieldCompiler.compileAllFields()

         // Expressions on AnonymousTypes are to support top-level declarations
         // of things like projection scope
         // eg find { ... } as { ... }[] by [SomeCollectionToIterate]
         val expression = if (anonymousTypeDefinition.accessor()?.scalarAccessorExpression() != null) {
            ExpressionCompiler(
               this,
               typeChecker,
               errors,
               fieldCompiler
            ).compileScalarAccessor(anonymousTypeDefinition.accessor().scalarAccessorExpression())
               .collectErrors(errors)
               .map { accessor ->
                  if (accessor is Expression) {
                     accessor
                  } else {
                     // Not supporting Accessor expressons here, as we're trying
                     // to capture top-level response object expressions.
                     // However, can enrich this in future if required.
                     null
                  }
               }
               .getOrElse { null }
         } else null
         compiledFields to expression
      }

      val fieldsFromConcreteProjectedToType = resolutionContext.concreteProjectionTypeContext?.let {
         this.parseType(namespace, it).map { type ->
            (type as ObjectType?)?.fields
         }
      }


      val anonymousTypeFields = if (fieldsFromConcreteProjectedToType is Either.Right) {
         fieldsFromConcreteProjectedToType.value?.let { fields.plus(it) } ?: fields
      } else {
         fields
      }


      return this.typeSystem.register(
         ObjectType(
            QualifiedName(namespace, typeName).fullyQualifiedName,
            ObjectTypeDefinition(
               fields = anonymousTypeFields.toSet(),
               annotations = annotations.toSet(),
               modifiers = listOf(),
               formatAndOffset = null,
               inheritsFrom = setOfNotNull(resolutionContext.baseType),
               expression = expression,
               compilationUnit = anonymousTypeDefinition.toCompilationUnit(),
               isAnonymous = true
            )
         )
      ).right()

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
      listOfInheritedTypes: ListOfInheritedTypesContext?
   ): Set<Type> {
      if (listOfInheritedTypes == null) return emptySet()
      return listOfInheritedTypes.typeReference().mapNotNull { typeTypeContext ->

         parseInheritedType(namespace, typeTypeContext) {
            when (it) {
               is EnumType -> CompilationError(typeTypeContext.start, "A Type cannot inherit from an Enum").asList()
                  .left()

               else -> it.right()
            }
         }

      }.toSet()
   }

   private fun parseEnumInheritance(
      namespace: Namespace,
      enumInheritedTypeContext: EnumInheritedTypeContext?
   ): Type? {
      if (enumInheritedTypeContext == null) return null

      val typeTypeContext = enumInheritedTypeContext.typeReference()
      return parseInheritedType(namespace, typeTypeContext) {
         when (it) {
            !is EnumType -> CompilationError(typeTypeContext.start, "An Enum can only inherit from an Enum").asList()
               .left()

            else -> it.right()
         }
      }

   }

   private inline fun parseInheritedType(
      namespace: Namespace,
      typeTypeContext: TypeReferenceContext,
      filter: (Type) -> Either<List<CompilationError>, Type>
   ): Type? {
      val inheritedTypeOrError = parseType(namespace, typeTypeContext)

      val inheritedEnumTypeOrError = if (inheritedTypeOrError.isRight()) {
         filter(inheritedTypeOrError.getOrElse { null }!!)
      } else inheritedTypeOrError

      return inheritedEnumTypeOrError
         .getOrElse {
            this.errors.addAll(it)
            null
         }
   }


   fun parseModifiers(typeModifier: MutableList<TypeModifierContext>): List<Modifier> {
      return typeModifier.map { Modifier.fromToken(it.text) }
   }

   internal fun collateAnnotations(annotations: List<AnnotationContext>): List<Annotation> {
      val result = annotations.map { annotation ->
         val annotationName = annotation.qualifiedName().text
         val annotationType = resolveUserType(
            annotation.findNamespace(),
            annotationName,
            annotation,
            symbolKind = SymbolKind.ANNOTATION
         )
            .wrapErrorsInList()
            .flattenErrors()

         mapAnnotationParams(
            annotation,
            annotationType.getOrNull() as? AnnotationType
         ).flatMap { annotationParameters ->

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
      annotation: AnnotationContext,
      annotationParameters: Map<String, Any>
   ): Either<List<CompilationError>, Annotation> {
      return Annotation(type, annotationParameters).right()
   }

   private fun mapAnnotationParams(
      annotation: AnnotationContext,
      annotationType: AnnotationType?
   ): Either<List<CompilationError>, Map<String, Any>> {
      return when {
         annotation.elementValue() != null -> {
            parseElementValue(annotation.elementValue()).map { mapOf("value" to it) }
         }

         annotation.elementValuePairs() != null -> mapElementValuePairs(annotation.elementValuePairs())
         else -> emptyMap<String, Any>().right()// No params specified
      }.flatMap { annotationParams ->
         if (annotationType != null) {
            mapTypedAnnotationParams(annotation, annotationType, annotationParams)
         } else {
            annotationParams.right()
         }
      }
   }

   private fun mapTypedAnnotationParams(
      annotation: AnnotationContext,
      type: AnnotationType,
      annotationParameters: Map<String, Any>
   ): Either<List<CompilationError>, Map<String, Any>> {
      val mutableParams = annotationParameters.toMutableMap()
      val fieldErrors = type.fields.mapNotNull { field ->
         if (!annotationParameters.containsKey(field.name) && !field.nullable) {
            if (field.accessor is LiteralExpression) {
               mutableParams[field.name] = (field.accessor as LiteralExpression).value
               null
            } else {
               CompilationError(
                  annotation.start,
                  "Annotation ${type.qualifiedName} requires member '${field.name}' which was not supplied"
               )
            }
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
         mutableParams.right()
      } else {
         allErrors.left()
      }
   }

   private fun mapElementValuePairs(tokenRule: ElementValuePairsContext): Either<List<CompilationError>, Map<String, Any>> {
      val pairs = tokenRule.elementValuePair() ?: return emptyMap<String, Any>().right()
      return pairs.map { keyValuePair ->
         parseElementValue(keyValuePair.elementValue()).map { parsedValue ->
            keyValuePair.identifier().text to parsedValue
         }
      }.invertEitherList().flattenErrors()
         .map { parsedAnnotationPropertyPairs: List<Pair<String, Any>> -> parsedAnnotationPropertyPairs.toMap() }
   }

   private fun parseElementValue(elementValue: ElementValueContext): Either<List<CompilationError>, Any> {
      return when {
         elementValue.literal() != null -> elementValue.literal().value().right()
         elementValue.qualifiedName() != null -> resolveEnumMember(elementValue.qualifiedName())
         else -> error("Unhandled element value: ${elementValue.text}")
      }
   }

   private fun parseTypeOrVoid(
      namespace: Namespace,
      returnType: OperationReturnTypeContext?
   ): Either<List<CompilationError>, Type> {
      return if (returnType == null) {
         VoidType.VOID.right()
      } else {
         parseType(namespace, returnType.typeReference())
      }
   }

   internal fun parseTypeOrUnionType(
      typeReference: NullableTypeReferenceContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()

   ):Either<List<CompilationError>, Type> {
      return when {
         typeReference.typeReference() != null -> parseType(typeReference.findNamespace(), typeReference.typeReference(), typeArgumentsInScope)
         typeReference.unionType() != null -> parseUnionType(typeReference.unionType(), typeArgumentsInScope)
         else -> listOf(CompilationError(typeReference.toCompilationUnit(), "Type expected")).left()
      }
   }

   private fun parseUnionType(unionType: UnionTypeContext, typeArgumentsInScope: List<TypeArgument>): Either<List<CompilationError>, Type> {
      return unionType.typeReference()
         .map { unionTypeMember ->
            parseType(
               unionType.findNamespace(),
               unionTypeMember,
               typeArgumentsInScope
               )
         }.invertEitherList()
         .flattenErrors()
         .map { types ->
            typeSystem.registerToken(UnionType(
               types,
               null,
               emptyList(),
               unionType.toCompilationUnit()
            )) as Type

         }
   }

   internal fun parseType(
      namespace: Namespace,
      typeType: TypeReferenceContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, Type> {
      return typeOrError(namespace, typeType, typeArgumentsInScope)
   }

   internal fun parseTypeOrFunction(
      typeType: FieldTypeDeclarationContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, ImportableToken> {
      return resolveTypeOrFunction(
         typeType.nullableTypeReference().typeReference().qualifiedName(),
         typeType.nullableTypeReference().typeReference().typeArguments(),
         typeType
      )
   }


   // Can remove this, it used to do more.
   internal fun parseType(
      namespace: Namespace,
      typeType: FieldTypeDeclarationContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, FieldTypeSpec> {

      val type = typeOrError(namespace, typeType, typeArgumentsInScope)
      return type
   }

   internal fun parseModelAttributeTypeReference(
      namespace: Namespace,
      modelAttributeReferenceCtx: ModelAttributeTypeReferenceContext
   ):
      Either<List<CompilationError>, Pair<QualifiedName, Type>> {
      val memberSourceTypeType = modelAttributeReferenceCtx.typeReference().first()
      val memberTypeType = modelAttributeReferenceCtx.typeReference()[1]
      val sourceTypeName = try {
         QualifiedName.from(lookupSymbolByName(memberSourceTypeType)).right()
      } catch (e: Exception) {
         CompilationError(
            modelAttributeReferenceCtx.start,
            "Only Model AttributeReference expressions (SourceType::FieldType) are allowed for views"
         ).asList().left()
      }
      return sourceTypeName.flatMap { memberSourceType ->
         this.parseType(namespace, memberTypeType).flatMap { memberType ->
            Pair(memberSourceType, memberType).right()
         }
      }
   }

   fun parseProjectionScope(
      expressionInputs: ExpressionInputsContext?,
      projectionSourceType: FieldTypeSpec
   ): Either<List<CompilationError>, ProjectionFunctionScope> {
      if (expressionInputs == null || expressionInputs.expressionInput().isEmpty()) {
         // MP: 2-Feb-23 : That was projectionSourceType.type
         // But have changed it, because in inline projections of entires, we're receiving
         // the array.
         // This might break something.
         return ProjectionFunctionScope.implicitThis(projectionSourceType.projectionType).right()
      }
      return when (expressionInputs.expressionInput().size) {
         1 -> {
            val input = expressionInputs.expressionInput().single()
            val identifier = input.identifier()?.text ?: ProjectionFunctionScope.THIS
            resolveTypeOrFunction(
               input.typeReference().qualifiedName(),
               null,
               input.typeReference().qualifiedName()
            ).map { token ->
               if (input.typeReference().arrayMarker() != null) {
                  ArrayType.of(token as Type)
               } else {
                  token
               }
            }.flatMap { token ->
               if (token is Type) {
                  ProjectionFunctionScope(identifier, token).right()
               } else {
                  listOf(
                     CompilationError(
                        input.typeReference().qualifiedName().toCompilationUnit(),
                        "Expected a type here"
                     )
                  ).left()
               }
            }
         }

         else -> listOf(
            CompilationError(
               expressionInputs.expressionInput()[1].toCompilationUnit(),
               "Expected a single parameter declaration"
            )
         ).left()
      }
   }

   fun parseAnonymousType(
      namespace: String,
      anonymousTypeDefinition: AnonymousTypeDefinitionContext,
      anonymousTypeName: String = NameGenerator.generate(),
      resolutionContext: ResolutionContext = ResolutionContext(),
   ): Either<List<CompilationError>, Type> {
      return compileAnonymousType(namespace, anonymousTypeName, anonymousTypeDefinition, resolutionContext)
   }

   internal fun typeOrError(
      namespace: Namespace,
      typeType: FieldTypeDeclarationContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, FieldTypeSpec> {
      return when {
         typeType.inlineInheritedType() != null -> compileInlineInheritedType(
            namespace,
            typeType
         ).map { FieldTypeSpec.forType(it) }

         typeType.aliasedType() != null -> compileInlineTypeAlias(namespace, typeType).map { FieldTypeSpec.forType(it) }
         else -> {
            resolveTypeOrFunction(
               typeType.nullableTypeReference().typeReference().qualifiedName(),
               typeType.nullableTypeReference().typeReference().typeArguments(),
               typeType
            )
               .map { token ->
                  if (token is Type && typeType.nullableTypeReference().typeReference().arrayMarker() != null) {
                     ArrayType(token, typeType.toCompilationUnit())
                  } else {
                     token
                  }
               }
               .flatMap { token: ImportableToken ->
                  when (token) {
                     is Type -> {
                        FieldTypeSpec.forType(token).right()
                     }

                     is Function -> {
                        // At this point, there were no argument provided.
                        // (Otherwise we'd have ended up in a different part of the parse tree).
                        // So, it's a no-arg function
                        if (token.parameters.isNotEmpty()) {
                           // So... this should neve happen.
                           listOf(
                              CompilationError(
                                 typeType.toCompilationUnit(),
                                 "Function ${token.qualifiedName} expects ${token.parameters.size} parameters, but none were provided"
                              )
                           )
                              .left()
                        } else {
                           val accessor = FunctionAccessor.buildAndResolveTypeArguments(token, emptyList())
                           FieldTypeSpec.forFunction(accessor).right()
                        }
                     }

                     else -> error("Was not expecting a ImportableToken of type ${token::class.simpleName}")
                  }
               }

//            typeOrError(namespace, typeType.nullableTypeReference().typeReference(), typeArgumentsInScope)
         }
      }
   }

   internal fun typeOrError(
      typeType: TypeReferenceContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, Type> = typeOrError(typeType.findNamespace(), typeType, typeArgumentsInScope)


   internal fun typeOrError(
      namespace: Namespace,
      typeType: TypeReferenceContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, Type> {
      val referencedGenericTypeArgument = typeArgumentsInScope.firstOrNull {
         it.declaredName == typeType.qualifiedName().identifier().text()
      }
      return when {
         referencedGenericTypeArgument != null -> referencedGenericTypeArgument.right()
         typeType.qualifiedName() != null -> resolveUserType(
            namespace,
            typeType.qualifiedName(),
            typeType.typeArguments(),
            typeArgumentsInScope = typeArgumentsInScope
         )
//         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text).right()
         else -> TODO("Unhandled when branch in typeOrError for typeContext with text ${typeType.text}")
      }.map { type ->
         // MP 28-Sept This is a recent bugfix, where previously all callers
         // had to check and wrap the T[] into an array type.
         // However, that's inconsistent with the return type Of Array<T> and T[].
         // So, fixing it here.
         // It's possible that leads to failing tests where we're ending up with T[][].
         // If so, fix the call site.  I've tried to find them all, but may have missed one.
         if (typeType.arrayMarker() != null) {
            ArrayType(type, typeType.toCompilationUnit())
         } else {
            type
         }
      }
   }

   private fun resolveGenericTypeArgument(
      declaringTypeOrFunctionName: String,
      referencedGenericTypeArgument: TypeReferenceContext
   ): TypeArgument {
      val typeName = listOf(declaringTypeOrFunctionName, "$", referencedGenericTypeArgument.text).joinToString("")
      return TypeArgument(
         qualifiedName = typeName,
         declaredName = referencedGenericTypeArgument.text,
         compilationUnits = referencedGenericTypeArgument.toCompilationUnits()
      )

   }

   fun parseTypeFormat(
      annotations: List<Annotation>,
      declaredType: Type,
      ctx: ParserRuleContext
   ): Either<List<CompilationError>, FormatsAndZoneOffset?> {

      val formatAnnotations =
         annotations.filter { it.type?.qualifiedName == BuiltIns.FormatAnnotation.name.fullyQualifiedName }
      if (formatAnnotations.isEmpty()) {
         return (null as? FormatsAndZoneOffset?).right()
      }
      val offsets = formatAnnotations.map { it.parameter("offset") as Int }
         .filter { it != 0 }
         .distinct()
      val formats = formatAnnotations.mapNotNull { it.parameter("value") as String? }
      if (offsets.size > 1) {
         return listOf(
            CompilationError(
               ctx.toCompilationUnit(),
               "It is invalid to declare multiple offsets for a format."
            )
         ).left()
      }
      val offset = offsets.singleOrNull()
      if (offset != null) {
         if (offset != 0 && !declaredType.inheritsFrom(PrimitiveType.INSTANT)) {
            return listOf(
               CompilationError(
                  ctx.toCompilationUnit(),
                  "Offset is only applicable to Instant based types"
               )
            ).left()
         }
         //// https://en.wikipedia.org/wiki/List_of_UTC_time_offsets
         if (offset > 840 || offset < -720) {
            return listOf(
               CompilationError(
                  ctx.toCompilationUnit(),
                  "Offset value can't be larger than 840 (UTC+14) or smaller than -720 (UTC-12)"
               )
            ).left()
         }
      }
      return FormatsAndZoneOffset(formats, offset).right()
   }

   private fun compileInlineInheritedType(
      namespace: Namespace,
      typeType: FieldTypeDeclarationContext
   ): Either<List<CompilationError>, Type> {
      return parseType(namespace, typeType.inlineInheritedType().typeReference()).map { inlineInheritedType ->
         val declaredTypeName = typeType.nullableTypeReference().typeReference().qualifiedName().identifier().text()

         typeSystem.register(
            ObjectType(
               QualifiedName(namespace, declaredTypeName).fullyQualifiedName,
               ObjectTypeDefinition(
                  inheritsFrom = setOf(inlineInheritedType),
                  compilationUnit = typeType.toCompilationUnit()
               )
            )
         )
      }
   }

   /**
    * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
    * rather than those declared explicitly (type alias PersonFirstName as String)
    */
   private fun compileInlineTypeAlias(
      namespace: Namespace,
      aliasTypeDefinition: FieldTypeDeclarationContext
   ): Either<List<CompilationError>, Type> {
      return parseType(namespace, aliasTypeDefinition.aliasedType().typeReference()).map { aliasedType ->
         val declaredTypeName =
            aliasTypeDefinition.nullableTypeReference().typeReference().qualifiedName().identifier().text()
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
      symbolKind: SymbolKind = SymbolKind.TYPE
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
      symbolKind: SymbolKind = SymbolKind.TYPE,
      // Method which takes the resolved qualified name, and checks to see if there is an unparsed token.
      // If so, the method should compile the token and return the compilation result in the either.
      // If not, then the method should return null.
      unparsedCheckAndCompile: (String) -> Either<List<CompilationError>, ImportableToken>?
   ): Either<List<CompilationError>, ImportableToken> {
      return attemptToLookupSymbolByName(namespace, requestedTypeName, context, symbolKind)
         .wrapErrorsInList()
         .flatMap { qualifiedTypeName ->
            if (typeSystem.contains(qualifiedTypeName, symbolKind)) {
               return@flatMap typeSystem.getTokenOrError(qualifiedTypeName, context, symbolKind)
                  .wrapErrorsInList()
                  .flatMap { importableToken ->
                     if (importableToken is DefinableToken<*> && !importableToken.isDefined) {
                        unparsedCheckAndCompile(qualifiedTypeName) ?: importableToken.right()
                     } else {
                        importableToken.right()
                     }
                  }
            }

            // Check to see if the token is unparsed, and
            // ccmpile if so
            val compilationResult = unparsedCheckAndCompile(qualifiedTypeName)
            if (compilationResult != null) {
               return@flatMap compilationResult
            }

            // Note: Use requestedTypeName, as qualifying it to the local namespace didn't help
            val error = {
               Errors.unresolvedType(requestedTypeName, context.toCompilationUnit()).asList()
            }

            if (ArrayType.isArrayTypeName(requestedTypeName)) {
               return@flatMap ArrayType.untyped().right()
            }

            if (StreamType.isStreamTypeName(requestedTypeName)) {
               return@flatMap StreamType.untyped().right()
            }
            if (MapType.isMapTypeName(requestedTypeName)) {
               return@flatMap MapType.untyped().right()
            }
            if (TypeReference.isTypeReferenceTypeName(requestedTypeName)) {
               return@flatMap TypeReference.untyped().right()
            }

            val requestedNameIsQualified = requestedTypeName.contains(".")
            if (!requestedNameIsQualified) {
               val importedTypeName = imports.firstOrNull { it.typeName == requestedTypeName }
               if (importedTypeName != null) {
                  typeSystem.getTokenOrError(importedTypeName.parameterizedName, context).wrapErrorsInList()
               } else {
                  error().left()
               }
            } else {
               error().left()
            }
         }
   }

   private fun resolveUserType(
      namespace: Namespace,
      className: QualifiedNameContext,
      typeArgumentCtx: TypeArgumentsContext? = null,
      symbolKind: SymbolKind = SymbolKind.TYPE,
      typeArgumentsInScope: List<TypeArgument> = emptyList()
   ): Either<List<CompilationError>, Type> {
      val typeArgumentTokens = typeArgumentCtx?.typeReference() ?: emptyList()
      return typeArgumentTokens.map { typeArgument -> parseType(namespace, typeArgument, typeArgumentsInScope) }
         .invertEitherList()
         .flattenErrors()
         .flatMap { typeArguments ->
            resolveUserType(namespace, className.identifier().text(), className, symbolKind)
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
      symbolKind: SymbolKind = SymbolKind.TYPE
   ): Either<List<CompilationError>, Type> {
      return resolveUserType(namespace, requestedTypeName, importsInSource(context), context, symbolKind)
   }

   internal fun resolveTypeOrFunction(
      tokenName: QualifiedNameContext,
      typeArgumentCtx: TypeArgumentsContext? = null,
      context: ParserRuleContext
   ): Either<List<CompilationError>, ImportableToken> {
      val type = resolveUserType(
         context.findNamespace(),
         tokenName,
         typeArgumentCtx
      )
      return type.handleErrorWith { errors ->
         when {
            // If the only issue is that we couldn't find the type, check to see if it's a function
            errors.all { it.errorCode == ErrorCodes.UNRESOLVED_TYPE.errorCode } -> {
               resolveFunction(tokenName, context).mapLeft {
                  listOf(
                     Errors.unresolvedType(
                        tokenName.identifier().text(),
                        context.toCompilationUnit()
                     )
                  )
               }
            }
            // Otherwise, return the errors
            else -> errors.left()
         }
      }
   }


   internal fun resolveImportableToken(
      tokenName: String,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_FUNCTION
   ): Either<List<CompilationError>, ImportableToken> {
      val namespace = context.findNamespace()
      return resolveUserToken(
         namespace,
         tokenName,
         importsInSource(context),
         context,
         symbolKind
      ) { qualifiedName ->
         if (tokens.unparsedFunctions.contains(qualifiedName)) {
            compileFunction(tokens.unparsedFunctions[qualifiedName]!!, qualifiedName)
         } else if (tokens.containsUnparsedType(qualifiedName, SymbolKind.TYPE)) {
            compileToken(qualifiedName)
            typeSystem.getTypeOrError(qualifiedName, context).wrapErrorsInList()
               .map { it }
         } else if (tokens.containsUnparsedService(qualifiedName)) {
            getOrCompileService(qualifiedName)
         } else {
            null
         }
      }
   }

   private fun getOrCompileService(qualifiedName: String): Either<List<CompilationError>, Service> {
      return services.firstOrNull { it.qualifiedName == qualifiedName }?.right()
         ?: compileService(qualifiedName, tokens.unparsedServices[qualifiedName]!!)
   }

   internal fun resolveImportableToken(
      tokenName: QualifiedNameContext,
      context: ParserRuleContext,
      symbolKind: SymbolKind = SymbolKind.TYPE_OR_FUNCTION
   ): Either<List<CompilationError>, ImportableToken> {
      return resolveImportableToken(tokenName.identifier().text(), context, symbolKind)
   }

   internal fun resolveFunction(
      requestedFunctionName: QualifiedNameContext,
      context: ParserRuleContext
   ): Either<List<CompilationError>, Function> {
      return resolveImportableToken(requestedFunctionName, context)
         .map { it as Function }
   }

   internal fun resolveFunction(
      requestedFunctionName: String,
      context: ParserRuleContext
   ): Either<List<CompilationError>, Function> {
      return resolveImportableToken(requestedFunctionName, context)
         .map { it as Function }
   }

   private fun importsInSource(context: ParserRuleContext): List<QualifiedName> {
      return tokens.importedTypeNamesInSource(context.source().normalizedSourceName)
   }


   private fun compileEnum(
      namespace: Namespace,
      typeName: String,
      ctx: EnumDeclarationContext
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

   private fun compileEnumValueExtensions(enumConstants: EnumConstantExtensionsContext?): List<EnumValueExtension> {
      return enumConstants?.enumConstantExtension()?.map { constantExtension ->
         EnumValueExtension(
            constantExtension.identifier().text,
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
      enumConstants: EnumConstantsContext?
   ): Either<List<CompilationError>, List<EnumValue>> {
      @Suppress("IfThenToElvis")
      return if (enumConstants == null) {
         emptyList<EnumValue>().right()
      } else {
         enumConstants.enumConstant().map { enumConstant ->
            val annotations = collateAnnotations(enumConstant.annotation())
            val name = unescape(enumConstant.identifier().text)
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
      token: EnumConstantsContext
   ): Either<List<CompilationError>, List<EnumValue>> {
      val defaults = enumValues.filter { it.isDefault }
      if (defaults.size > 1) {
         return listOf(
            CompilationError(
               token.start,
               "Cannot declare multiple default values - found ${defaults.joinToString { it.name }}"
            )
         ).left()
      } else {
         return enumValues.right()
      }
   }

   /**
    * Returns a set of references to enum values that this enum value declares a synonym to.
    * Note that because of compilation order, a result from this method guarantees that the
    * enum exists, but NOT that the value on the enum exists.
    * That's handled later when synonyms are resolved.
    */
   private fun parseSynonyms(enumConstant: EnumConstantContext): Either<List<CompilationError>, List<EnumValueQualifiedName>> {
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
   fun resolveEnumMember(enumQualifiedNameReference: QualifiedNameContext): Either<List<CompilationError>, EnumMember> {
      return resolveEnumMember(enumQualifiedNameReference.identifier().text(), enumQualifiedNameReference)
   }

   /**
    * Returns an enum member - requires that the enum has already been compiled.
    * This asserts that both the enum exists, and that it contains the requested member.
    * Use resolveEnumValueName if you need to handle circular references, where the enum may not
    * have already been compiled.
    */
   fun resolveEnumMember(enumMemberName: String, token: ParserRuleContext): Either<List<CompilationError>, EnumMember> {
      return resolveEnumReference(enumMemberName, token) { enumType, enumValueName ->
         when {
            !enumType.isDefined -> {
               // This happens if there's an enum with a circular reference.
               // That's supported, but we defer
               CompilationError(
                  token.start,
                  "An internal error occurred processing ${enumType.qualifiedName}, attempting to resolve an EnumMember on a non-compiled enum - use resolveEnumValueName, or compile the enum first."
               ).asList().left()
            }

            !enumType.has(enumValueName) -> CompilationError(
               token.start,
               "${enumType.qualifiedName} does not have a member $enumValueName"
            ).asList().left()

            else -> enumType.member(enumValueName).right()
         }
      }
   }

   /**
    * Returns an EnumValueQualifiedName.
    * At this point, it is guaranteed that the enum exists, but NOT that the value is present.
    * This is to support use cases where there are circular references (ie., synonyms where two enums point at each other).
    * If you don't need to support that usecase, use resolveEnumMember, which guarantees both the enum and the value.
    */
   private fun resolveEnumValueName(enumQualifiedNameReference: QualifiedNameContext): Either<List<CompilationError>, EnumValueQualifiedName> {
      return resolveEnumReference(enumQualifiedNameReference) { enumType, enumValueName ->
         EnumValue.enumValueQualifiedName(enumType, enumValueName).right()
      }
   }

   private fun <T> resolveEnumReference(
      name: String,
      parserRuleContext: ParserRuleContext,
      enumSelector: (EnumType, String) -> Either<List<CompilationError>, T>
   ): Either<List<CompilationError>, T> {
      val (enumName, enumValueName) = Enums.splitEnumValueQualifiedName(name)

      return resolveUserType(
         parserRuleContext.findNamespace(),
         enumName.parameterizedName,
         parserRuleContext
      )
         .flatMap { enumType ->
            if (enumType is EnumType) {
               enumSelector(enumType, enumValueName)
            } else {
               CompilationError(parserRuleContext.start, "${enumType.qualifiedName} is not an Enum").asList()
                  .left()
            }
         }
   }

   private fun <T> resolveEnumReference(
      enumQualifiedNameReference: QualifiedNameContext,
      enumSelector: (EnumType, String) -> Either<List<CompilationError>, T>
   ): Either<List<CompilationError>, T> {
      return resolveEnumReference(
         enumQualifiedNameReference.identifier().text(),
         enumQualifiedNameReference,
         enumSelector
      )
   }


   private fun compileEnumExtension(
      namespace: Namespace,
      typeRule: EnumExtensionDeclarationContext
   ): CompilationError? {
      val enumValues = compileEnumValueExtensions(typeRule.enumConstantExtensions())
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc())

      return attemptToLookupSymbolByName(namespace, typeRule.identifier().text, typeRule)
         .flatMap { typeName ->
            val enum = typeSystem.getType(typeName) as EnumType
            enum.addExtension(EnumExtension(enumValues, annotations, typeRule.toCompilationUnit(), typeDoc = typeDoc))
               .toCompilationError(typeRule.start)
         }.errorOrNull()
   }

   internal fun parseTypeDoc(content: TypeDocContext?): String? {
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
      namespaceAndParserContext: Pair<Namespace, FunctionDeclarationContext>,
      qualifiedName: String
   ): Either<List<CompilationError>, Function> {
      if (typeSystem.isDefined(qualifiedName)) {
         // The function may have already been compiled
         // if it's been used inline.
         // That's ok, we can just return it out of the type system
         return typeSystem.getFunction(qualifiedName).right()
      }
      val (namespace, functionToken) = namespaceAndParserContext
      val typeArguments = (functionToken.typeArguments()?.typeReference() ?: emptyList()).map { typeType ->
         resolveGenericTypeArgument(qualifiedName, typeType)
      }
      return parseType(namespace, functionToken.typeReference(), typeArguments).flatMap { returnType ->
         val parameters =
            functionToken.operationParameterList()?.operationParameter()?.mapIndexed { index, parameterDefinition ->
               val anonymousParameterTypeName = "$qualifiedName\$Param$index"
               parseParameter(namespace, parameterDefinition, typeArguments, anonymousParameterTypeName)
            }?.reportAndRemoveErrorList(errors) ?: emptyList()

         if (functionToken.functionModifiers() != null && functionToken.functionModifiers().text != "query") {
            return@flatMap CompilationError(
               functionToken.start,
               "Only query function modifier is allowed!"
            ).asList().left()
         }

         val functionAttributes = functionToken.functionModifiers()?.let {
            EnumSet.of(FunctionModifiers.Query)
         }
            ?: EnumSet.noneOf(FunctionModifiers::class.java)

         val typeDoc = parseTypeDoc(functionToken.typeDoc())
         val function = Function(
            qualifiedName,
            FunctionDefinition(
               parameters, returnType, functionAttributes, typeArguments, typeDoc, functionToken.toCompilationUnit()
            )
         )
         this.functions.add(function)
         this.typeSystem.register(function)

         function.right()
      }
   }

   private fun compileService(
      qualifiedName: String,
      serviceTokenPair: Pair<Namespace, ServiceDeclarationContext>
   ):
      Either<List<CompilationError>, Service> {
      val (_, serviceToken) = serviceTokenPair
      val serviceDoc = parseTypeDoc(serviceToken.typeDoc())
      val serviceLineage = if (serviceToken.serviceBody().lineageDeclaration() != null) {
         val lineageDeclaration = serviceToken.serviceBody().lineageDeclaration()
         val consumes = mutableListOf<ConsumedOperation>()
         val stores = mutableListOf<QualifiedName>()
         lineageDeclaration.lineageBody().lineageBodyMember().forEach { lineageBodyMemberContext ->
            when {
               lineageBodyMemberContext.consumesBody() != null -> {
                  val consumeQualifiedName = lineageBodyMemberContext.consumesBody().qualifiedName().text
                  val operationOrError =
                     typeSystem.getOperationOrError(consumeQualifiedName, lineageBodyMemberContext.consumesBody())
                  when (operationOrError) {
                     is Either.Left -> return operationOrError.value.asList().left()
                     is Either.Right -> consumes.add(operationOrError.value)
                  }
               }

               lineageBodyMemberContext.storesBody() != null -> {
                  val storeQualifiedName = lineageBodyMemberContext.storesBody().qualifiedName().text
                  if (!typeSystem.isDefined(storeQualifiedName)) {
                     return CompilationError(
                        lineageBodyMemberContext.storesBody().qualifiedName().start,
                        "unknown type $storeQualifiedName"
                     ).asList().left()
                  }
                  stores.add(QualifiedName.from(storeQualifiedName))

               }

               else -> {}
            }
         }

         val lineageAnnotations = collateAnnotations(lineageDeclaration.annotation())
         val lineageDoc = lineageDeclaration.typeDoc()
         ServiceLineage(
            consumes.toList(),
            stores.toList(),
            lineageAnnotations,
            lineageDeclaration.toCompilationUnits(),
            parseTypeDoc(lineageDoc)
         )
      } else {
         null
      }
      val members = serviceToken.serviceBody().serviceBodyMember().map { serviceBodyMember ->
         when {
            serviceBodyMember.serviceOperationDeclaration() != null -> compileOperation(serviceBodyMember.serviceOperationDeclaration())
            serviceBodyMember.queryOperationDeclaration() != null -> compileQueryOperation(serviceBodyMember.queryOperationDeclaration())
            serviceBodyMember.tableDeclaration() != null -> compileTable(serviceBodyMember.tableDeclaration())
            serviceBodyMember.streamDeclaration() != null -> compileStream(serviceBodyMember.streamDeclaration())
            else -> error("Unhandled type of service member. ")
         }
      }
         .reportAndRemoveErrorList(errors)
      val dependentTypes = members.flatMap {
         it.annotations.mapNotNull { annotation -> annotation.type } +
            it.parameters.map { parameter -> parameter.type } +
            it.returnType
      }.map { it.toQualifiedName() }
      val service = Service(
         qualifiedName,
         members,
         collateAnnotations(serviceToken.annotation()),
         listOf(serviceToken.toCompilationUnit(dependentTypes)),
         serviceDoc,
         serviceLineage
      )
      this.services.add(service)
      return service.right()

   }


   private fun compileServices() {
      val services = this.tokens.unparsedServices.map { (qualifiedName, serviceTokenPair) ->
         getOrCompileService(qualifiedName)
      }

      services.invertEitherList()
         .flattenErrors()
         .collectErrors(errors)
   }

   private fun compileQueryOperation(queryOperation: QueryOperationDeclarationContext): Either<List<CompilationError>, QueryOperation> {
      val namespace = queryOperation.findNamespace()
      return parseType(namespace, queryOperation.typeReference())
         .flatMap { returnType ->
            parseCapabilities(queryOperation).map { capabilities ->
               val name = queryOperation.identifier().text
               val grammar = queryOperation.queryGrammarName().identifier().text
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

   private fun compileStream(streamDeclaration: StreamDeclarationContext): Either<List<CompilationError>, ServiceMember> {
      val namespace = streamDeclaration.findNamespace()
      return parseType(namespace, streamDeclaration.typeReference())
         .flatMap { returnType ->
            if (!StreamType.isStreamTypeName(returnType.toQualifiedName())) {
               listOf(
                  CompilationError(
                     streamDeclaration.toCompilationUnit(),
                     "A stream operation must return a type of Stream<T>. Consider returning Stream<${returnType.toQualifiedName().typeName}>"
                  )
               ).left()
            } else {
               val name = streamDeclaration.identifier().text
               Stream(
                  name = name,
                  annotations = collateAnnotations(streamDeclaration.annotation()),
                  returnType = returnType,
                  compilationUnits = listOf(streamDeclaration.toCompilationUnit()),
                  typeDoc = parseTypeDoc(streamDeclaration.typeDoc())
               ).right()
            }
         }
   }

   private fun compileTable(tableDeclaration: TableDeclarationContext): Either<List<CompilationError>, ServiceMember> {
      val namespace = tableDeclaration.findNamespace()
      return parseType(namespace, tableDeclaration.typeReference())
         .flatMap { returnType ->
            if (!ArrayType.isTypedCollection(returnType.toQualifiedName())) {
               listOf(
                  CompilationError(
                     tableDeclaration.toCompilationUnit(),
                     "A table operation must return an array. Consider returning ${returnType.toQualifiedName().typeName}[]"
                  )
               ).left()
            } else {
               val name = tableDeclaration.identifier().text
               Table(
                  name = name,
                  annotations = collateAnnotations(tableDeclaration.annotation()),
                  returnType = returnType,
                  compilationUnits = listOf(tableDeclaration.toCompilationUnit()),
                  typeDoc = parseTypeDoc(tableDeclaration.typeDoc())
               ).right()
            }
         }
   }

   private fun parseCapabilities(queryOperation: QueryOperationDeclarationContext): Either<List<CompilationError>, List<QueryOperationCapability>> {
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

   private fun compileOperation(operationDeclaration: ServiceOperationDeclarationContext): Either<List<CompilationError>, Operation> {
      val signature = operationDeclaration.operationSignature()
      val namespace = operationDeclaration.findNamespace()
      return parseTypeOrVoid(namespace, signature.operationReturnType())
         .flatMap { returnType ->
            val scope = operationDeclaration.operationScope()?.identifier()?.text
            val operationParameters = signature.parameters().map { operationParameterContext ->
               parseParameter(namespace, operationParameterContext)
            }.reportAndRemoveErrorList(errors)

            parseOperationContract(operationDeclaration, returnType, namespace).map { contract ->
               Operation(
                  name = signature.identifier().text,
                  scope = OperationScope.forToken(scope),
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
      operationParameterContext: OperationParameterContext,
      typeArgumentsInScope: List<TypeArgument> = emptyList(),
      // When parsing paraeters that are lambdas we need a useful name
      anonymousParameterTypeName: String? = null
   ): Either<List<CompilationError>, lang.taxi.services.Parameter> {
      val paramTypeOrError: Either<List<CompilationError>, Type> =
         if (operationParameterContext.nullableTypeReference()?.typeReference() != null) {
            parseType(
               namespace,
               operationParameterContext.nullableTypeReference().typeReference(),
               typeArgumentsInScope
            )
         } else if (operationParameterContext.lambdaSignature() != null) {
            parseLambdaTypeParameter(
               operationParameterContext.lambdaSignature(),
               typeArgumentsInScope,
               anonymousParameterTypeName
            )
         } else {
            error("Unhandled branch in parameter parsing")
         }

      return paramTypeOrError.flatMap { paramType ->
         mapConstraints(
            operationParameterContext.parameterConstraintExpressionList(),
            paramType,
            namespace
         ).map { constraints ->
            val isNullable = operationParameterContext.nullableTypeReference()?.Nullable() != null
            val typeDoc = parseTypeDoc(operationParameterContext.typeDoc())
            val isVarargs = operationParameterContext.varargMarker() != null
            lang.taxi.services.Parameter(
               annotations = collateAnnotations(operationParameterContext.annotation()),
               type = paramType,
               name = operationParameterContext.parameterName()?.identifier()?.text,
               constraints = constraints,
               isVarArg = isVarargs,
               typeDoc = typeDoc,
               nullable = isNullable
            )
         }
      }
   }

   private fun parseLambdaTypeParameter(
      lambdaSignature: LambdaSignatureContext,
      typeArgumentsInScope: List<TypeArgument>,
      anonymousParameterTypeName: String?
   ): Either<List<CompilationError>, Type> {
      return lambdaSignature.expressionInputs().expressionInput().map { inputType ->
         parseType(
            lambdaSignature.findNamespace(),
            inputType.typeReference(),
            typeArgumentsInScope
         )
      }.invertEitherList().flattenErrors().flatMap { inputTypes ->
         parseType(
            lambdaSignature.findNamespace(),
            lambdaSignature.typeReference(),
            typeArgumentsInScope
         ).map { returnType ->
            LambdaExpressionType(
               qualifiedName = anonymousParameterTypeName!!,
               inputTypes,
               returnType,
               lambdaSignature.toCompilationUnits()
            )
         }
      }
   }

   private fun parseOperationContract(
      operationDeclaration: ServiceOperationDeclarationContext,
      returnType: Type,
      namespace: Namespace
   ): Either<List<CompilationError>, OperationContract?> {
      val signature = operationDeclaration.operationSignature()
      val constraintList = signature.operationReturnType()
         ?.parameterConstraintExpressionList()
         ?: return null.right()

      return OperationConstraintConverter(
         constraintList,
         returnType,
         typeResolver(namespace)
      ).constraints().map { constraints ->
         OperationContract(returnType, constraints)
      }
   }

   internal fun mapConstraints(
      constraintList: ExpressionGroupContext?,
      paramType: Type,
      fieldCompiler: FieldCompiler?,
      activeScopes: List<ProjectionFunctionScope> = emptyList()
   ): Either<List<CompilationError>, List<Constraint>> {
      if (constraintList == null) {
         return emptyList<Constraint>().right()
      }
      return expressionCompiler(fieldCompiler, activeScopes).compile(constraintList).map { expression ->
         listOf(ExpressionConstraint(expression))
      }
   }

   @Deprecated("Pass an ExpressionGroupContext instead")
   internal fun mapConstraints(
      constraintList: ParameterConstraintExpressionListContext?,
      paramType: Type,
      namespace: Namespace
   ): Either<List<CompilationError>, List<Constraint>> {
      if (constraintList == null) {
         return emptyList<Constraint>().right()
      }
      return OperationConstraintConverter(
         constraintList,
         paramType, typeResolver(namespace)
      ).constraints()
   }

   private fun compilePolicies() {
      this.tokens.unparsedPolicies.map { (name, namespaceTokenPair) ->
         val (namespace, token) = namespaceTokenPair

         parseType(namespace, token.typeReference()).map { targetType ->
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

         override fun resolve(context: TypeReferenceContext): Either<List<CompilationError>, Type> {
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

   private fun compilePolicyRulesets(namespace: String, token: PolicyDeclarationContext): List<RuleSet> {
      return token.policyRuleSet().map {
         compilePolicyRuleset(namespace, it)
      }
   }

   private fun compilePolicyRuleset(namespace: String, token: PolicyRuleSetContext): RuleSet {
      val operationType = token.policyOperationType().identifier()?.text
      val operationScope = PolicyOperationScope.parse(token.policyScope()?.text)
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

   private fun compilePolicyStatement(namespace: String, token: PolicyStatementContext): PolicyStatement {
      val (condition, instruction) = compileCondition(namespace, token)
      return PolicyStatement(condition, instruction, token.toCompilationUnit())
   }

   private fun compileCondition(
      namespace: String,
      token: PolicyStatementContext
   ): Pair<Condition, Instruction> {
      return when {
         token.policyCase() != null -> compileCaseCondition(namespace, token.policyCase())
         token.policyElse() != null -> ElseCondition() to Instructions.parse(token.policyElse().policyInstruction())
         else -> error("Invalid condition is neither a case nor an else")
      }
   }

   private fun compileCaseCondition(
      namespace: String,
      case: PolicyCaseContext
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

fun List<Either<List<CompilationError>, *>>.allValid(): Boolean {
   return this.all { it.isRight() }
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
      item.getOrElse { errors ->
         errorCollection.addAll(errors)
         null
      }
   }
}

fun <T : Any> List<Either<CompilationError, T>>.reportAndRemoveErrors(errorCollection: MutableList<CompilationError>): List<T> {
   return this.mapNotNull { it.reportIfCompilationError(errorCollection) }
}

private fun <T : Any> Either<CompilationError, T>.reportIfCompilationError(errorCollection: MutableList<CompilationError>): T? {
   return this.getOrElse { compilationError ->
      errorCollection.add(compilationError)
      null
   }
}

// Wrapper class to indicate that an underlying error has been captured, but handled
// This is primarily to stop us processing errors multiple times as they make their way
// up the stack
data class ReportedError(val error: CompilationError)

fun CompilationError.asList(): List<CompilationError> = listOf(this)

enum class SymbolKind {
   /**
    * A type or a model.  (Both are considered types from a symbol perspective)
    */
   TYPE,

   SERVICE,

   /**
    * Used where a token could reasonably be either type or function,
    * eg: a field declaration
    */
   TYPE_OR_FUNCTION,
   ANNOTATION,
   FUNCTION;

   fun matches(token: ParserRuleContext): Boolean {
      return when (this) {
//         MATCH_ANYTHING -> true
         ANNOTATION -> {
            token is AnnotationTypeDeclarationContext
         }

         TYPE -> {
            when (token) {
               is AnnotationTypeDeclarationContext -> false
               is TypeDeclarationContext -> true
               is EnumDeclarationContext -> true
               is TypeAliasDeclarationContext -> true
               is TypeReferenceContext -> true
               is FieldTypeDeclarationContext -> true
               else -> {
                  TODO("matches implementation not defined for token type ${token::class.simpleName}")
               }
            }
         }

         else -> {
            TODO("Matching on token type against symbol kind ${this.name} is not implemented.  Note - got passed a token of ${token::class.simpleName}")
         }
      }
   }

   fun matches(token: ImportableToken): Boolean {
      val isType = token is PrimitiveType || (token is UserType<*, *> && token !is AnnotationType)
      return when (this) {
//         MATCH_ANYTHING -> true
         TYPE -> isType
         FUNCTION -> token is Function
         TYPE_OR_FUNCTION -> isType || token is Function
         ANNOTATION -> token is AnnotationType
         SERVICE -> token is Service
      }
   }
}

