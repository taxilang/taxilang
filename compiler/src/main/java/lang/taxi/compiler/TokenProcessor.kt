package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.policies.*
import lang.taxi.services.*
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.log
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

internal class TokenProcessor(val tokens: Tokens, importSources: List<TaxiDocument> = emptyList(), collectImports: Boolean = true) {

   constructor(tokens: Tokens, collectImports: Boolean) : this(tokens, emptyList(), collectImports)

   private val typeSystem: TypeSystem
   private val synonymRegistry: SynonymRegistry<ParserRuleContext>
   private val services = mutableListOf<Service>()
   private val policies = mutableListOf<Policy>()
   private val dataSources = mutableListOf<DataSource>()
   private val constraintValidator = ConstraintValidator()

   private val errors = mutableListOf<CompilationError>()

   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)

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
      return errors to TaxiDocument(types, services.toSet(), policies.toSet(), dataSources.toSet())
   }

   fun findDeclaredTypeNames(): List<QualifiedName> {
      createEmptyTypes()

      // We need to check all the ObjectTypes, to see if they declare any inline type aliases
      val inlineTypeAliases = tokens.unparsedTypes.filter { (_, tokenPair) ->
         val (_, ctx) = tokenPair
         ctx is TaxiParser.TypeDeclarationContext
      }.flatMap { (name, tokenPair) ->
         val (namespace, ctx) = tokenPair
         val typeCtx = ctx as TaxiParser.TypeDeclarationContext
         val typeAliasNames = typeCtx.typeBody()?.typeMemberDeclaration()
            ?.filter { it.exception == null }
            ?.mapNotNull { memberDeclaration ->
               val fieldDeclaration = memberDeclaration.fieldDeclaration()
               if (fieldDeclaration.typeType() != null && fieldDeclaration.typeType().aliasedType() != null) {
                  // This is an inline type alias
                  qualify(namespace, memberDeclaration.fieldDeclaration().typeType())
               } else {
                  null
               }
            } ?: emptyList()
         typeAliasNames.map { QualifiedName.from(it) }
      }

      val declaredTypeNames = typeSystem.typeList().map { it.toQualifiedName() }
      return declaredTypeNames + inlineTypeAliases
   }


   private fun qualify(namespace: Namespace, type: TaxiParser.TypeTypeContext): String {
      return if (type.primitiveType() != null) {
         PrimitiveType.fromDeclaration(type.primitiveType()!!.text).qualifiedName
      } else {
         qualify(namespace, type.classOrInterfaceType().text)
      }
   }

   private fun qualify(namespace: Namespace, name: String): String {
      return typeSystem.qualify(namespace, name)

   }

   private fun compile() {
      createEmptyTypes()
      compileTokens()
      compileTypeExtensions()
      compileServices()
      compilePolicies()
      compileDataSources()

      applySynonymsToEnums()

      // Some validations can't be performed at the time, because
      // they rely on a fully parsed document structure
      validateConstraints()
   }

   private fun applySynonymsToEnums() {
      // Now we have a full picture of all the enums, we can
      // map the synonyms effectively
      val typesWithSynonyms = synonymRegistry.getTypesWithSynonymsRegistered()
      typeSystem.getTypes(includeImportedTypes = true) { typesWithSynonyms.contains(it.toQualifiedName()) }
         .filterIsInstance<EnumType>()
         .map { enum ->
            val valueExtensions = typesWithSynonyms.getValue(enum.toQualifiedName()).flatMap { enumValueQualifiedName ->
               val (_, enumValueName) = Enums.splitEnumValueQualifiedName(enumValueQualifiedName)
               val valueExtensions = synonymRegistry.synonymsFor(enumValueQualifiedName).map { (synonym, context) ->
                  EnumValueExtension(enumValueName, emptyList(), listOf(synonym), compilationUnit = context.toCompilationUnit())
               }
               valueExtensions
            }
            if (valueExtensions.isNotEmpty()) {
               // Bit of a hack here on the compilationUnit.  Not sure what to use
               enum.addExtension(EnumExtension(valueExtensions, compilationUnit = valueExtensions.first().compilationUnit))
            } else {
               error("Enum had synonyms registered but then none were found.  This shouldn't happen")
            }
         }

   }


   private fun validateConstraints() {
      constraintValidator.validateAll(typeSystem, services)
   }


   private fun createEmptyTypes() {
      tokens.unparsedTypes.forEach { tokenName, (_, token) ->
         when (token) {
            is TaxiParser.EnumDeclarationContext -> typeSystem.register(EnumType.undefined(tokenName))
            is TaxiParser.TypeDeclarationContext -> typeSystem.register(ObjectType.undefined(tokenName))
            is TaxiParser.TypeAliasDeclarationContext -> typeSystem.register(TypeAlias.undefined(tokenName))
         }
      }
   }

   private fun compileTokens() {
      tokens.unparsedTypes.forEach { (tokenName, _) ->
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
      when (tokenRule) {
         is TaxiParser.TypeDeclarationContext -> compileType(namespace, tokenName, tokenRule)
         is TaxiParser.EnumDeclarationContext -> compileEnum(namespace, tokenName, tokenRule)
         is TaxiParser.TypeAliasDeclarationContext -> compileTypeAlias(namespace, tokenName, tokenRule)
         // TODO : This is a bit broad - assuming that all typeType's that hit this
         // line will be a TypeAlias inline.  It could be a normal field declaration.
         is TaxiParser.TypeTypeContext -> compileInlineTypeAlias(namespace, tokenRule)
         else -> TODO("Not handled: $tokenRule")
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
      val typeName = qualify(namespace, typeRule.Identifier().text)
      val type = typeSystem.getType(typeName) as TypeAlias
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc())
      return type.addExtension(TypeAliasExtension(annotations, typeRule.toCompilationUnit(), typeDoc)).toCompilationError(typeRule.start)
   }

   private fun compileTypeExtension(namespace: Namespace, typeRule: TaxiParser.TypeExtensionDeclarationContext): CompilationError? {
      val typeName = qualify(namespace, typeRule.Identifier().text)
      val type = typeSystem.getType(typeName) as ObjectType
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc()?.source()?.content)
      val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
         val fieldName = member.typeExtensionFieldDeclaration().Identifier().text
         val fieldAnnotations = collateAnnotations(member.annotation())
         val refinedType = member.typeExtensionFieldDeclaration()?.typeExtensionFieldTypeRefinement()?.typeType()?.let {
            val refinedType = typeSystem.getType(qualify(namespace, it.text))
            assertTypesCompatible(type.field(fieldName).type, refinedType, fieldName, typeName, typeRule)
         }


         FieldExtension(fieldName, fieldAnnotations, refinedType)
      }
      val errorMessage = type.addExtension(ObjectTypeExtension(annotations, fieldExtensions, typeDoc, typeRule.toCompilationUnit()))
      return errorMessage.toCompilationError(typeRule.start)

   }

   private fun assertTypesCompatible(originalType: Type, refinedType: Type, fieldName: String, typeName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext): Type {
      val refinedUnderlyingType = TypeAlias.underlyingType(refinedType)
      val originalUnderlyingType = TypeAlias.underlyingType(originalType)

      if (originalUnderlyingType != refinedUnderlyingType) {
         throw CompilationException(typeRule.start, "Cannot refine field $fieldName on $typeName to ${refinedType.qualifiedName} as it maps to ${refinedUnderlyingType.qualifiedName} which is incompatible with the existing type of ${originalType.qualifiedName}", typeRule.source().sourceName)
      }
      return refinedType
   }

   private fun compileTypeAlias(namespace: Namespace, tokenName: String, tokenRule: TaxiParser.TypeAliasDeclarationContext) {
      val aliasedType: Either<ReportedError, Type> = parseType(namespace, tokenRule.aliasedType().typeType()).collectError()
      val annotations = collateAnnotations(tokenRule.annotation())

      aliasedType.map {
         val definition = TypeAliasDefinition(it, annotations, tokenRule.toCompilationUnit(), typeDoc = parseTypeDoc(tokenRule.typeDoc()))
         this.typeSystem.register(TypeAlias(tokenName, definition))
      }
   }

   private fun <T> Either<CompilationError, T>.collectError(): Either<ReportedError, T> {
      return this.mapLeft { error ->
         this@TokenProcessor.errors.add(error)
         ReportedError(error)
      }
   }

   private fun <T : Any> Iterable<Either<CompilationError, T>>.reportAndRemoveErrors(): List<T> {
      return this.mapNotNull { item ->
         item.getOrHandle { compilationError ->
            this@TokenProcessor.errors.add(compilationError)
            null
         }
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
         val fieldsWithConditions = conditionalFieldStructures.flatMap { it.fields }
         val fields = (typeBody.typeMemberDeclaration()?.map { member ->
            compileField(member, namespace)
         } ?: emptyList()).reportAndRemoveErrors() + fieldsWithConditions

         fields
      } ?: emptyList()

      val annotations = collateAnnotations(ctx.annotation())
      val modifiers = parseModifiers(ctx.typeModifier())
      val inherits = parseInheritance(namespace, ctx.listOfInheritedTypes())
      val typeDoc = parseTypeDoc(ctx.typeDoc()?.source()?.content)
      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields.toSet(), annotations.toSet(), modifiers, inherits, typeDoc, ctx.toCompilationUnit())))
   }

   internal fun compiledField(member: TaxiParser.TypeMemberDeclarationContext, namespace: Namespace): Field? {
      return compileField(member, namespace)
         .collectError()
         .orNull()
   }

   private fun compileField(member: TaxiParser.TypeMemberDeclarationContext, namespace: Namespace): Either<CompilationError, Field> {
      val fieldAnnotations = collateAnnotations(member.annotation())
      val accessor = compileAccessor(member.fieldDeclaration().accessor())
      val typeDoc = parseTypeDoc(member.typeDoc())
      return parseType(namespace, member.fieldDeclaration().typeType()).map { type ->
         Field(
            name = unescape(member.fieldDeclaration().Identifier().text),
            type = type,
            nullable = member.fieldDeclaration().typeType().optionalType() != null,
            modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
            annotations = fieldAnnotations,
            constraints = mapConstraints(member.fieldDeclaration().typeType(), type),
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

   internal fun compileAccessor(accessor: TaxiParser.AccessorContext?): Accessor? {
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
         expression.columnDefinition() != null -> ColumnAccessor(expression.columnDefinition().columnIndex().IntegerLiteral().text.toInt())
         else -> error("Unhandled type of accessor expression")
      }
   }

   private fun mapFieldModifiers(fieldModifier: TaxiParser.FieldModifierContext?): List<FieldModifier> {
      if (fieldModifier == null) return emptyList();
      val modifier = FieldModifier.values().firstOrNull { it.token == fieldModifier.text }
         ?: error("Unknown field modifier: ${fieldModifier.text}")
      return listOf(modifier)
   }

   private fun unescape(text: String): String {
      return text.removeSurrounding("`")
   }

   private fun parseInheritance(namespace: Namespace, listOfInheritedTypes: TaxiParser.ListOfInheritedTypesContext?): Set<Type> {
      if (listOfInheritedTypes == null) return emptySet()
      return listOfInheritedTypes.typeType().map { typeTypeContext ->
         parseType(namespace, typeTypeContext)
      }.reportAndRemoveErrors().toSet()
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

   internal fun paredType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Type? {
      return parseType(namespace, typeType)
         .collectError()
         .orNull()
   }

   internal fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      val type = when {
//            typeType.aliasedType() != null -> compileInlineTypeAlias(typeType)
         typeType.classOrInterfaceType() != null -> resolveUserType(namespace, typeType.classOrInterfaceType())
         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text).right()
         else -> throw IllegalArgumentException()
      }
      return type.map {
         if (typeType.listType() != null) {
            ArrayType(it, typeType.toCompilationUnit())
         } else {
            it
         }
      }
   }

   /**
    * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
    * rather than those declared explicitly (type alias PersonFirstName as String)
    */
   private fun compileInlineTypeAlias(namespace: Namespace, aliasTypeDefinition: TaxiParser.TypeTypeContext): Either<CompilationError, Type> {
      return parseType(namespace, aliasTypeDefinition.aliasedType().typeType()).map { aliasedType ->
         val typeAliasName = qualify(namespace, aliasTypeDefinition.classOrInterfaceType().Identifier().text())
         // Annotations not supported on Inline type aliases
         val annotations = emptyList<Annotation>()
         val typeAlias = TypeAlias(typeAliasName, TypeAliasDefinition(aliasedType, annotations, aliasTypeDefinition.toCompilationUnit()))
         typeSystem.register(typeAlias)
         typeAlias
      }

   }

   private fun resolveUserType(namespace: Namespace, requestedTypeName: String, imports: List<QualifiedName>, context: ParserRuleContext): Either<CompilationError, Type> {
      val qualifiedTypeName = qualify(namespace, requestedTypeName)
      if (typeSystem.contains(qualifiedTypeName)) {
         return typeSystem.getTypeOrError(qualifiedTypeName, context)
      }

      if (tokens.unparsedTypes.contains(qualifiedTypeName)) {
         compileToken(qualifiedTypeName)
         return typeSystem.getTypeOrError(qualifiedTypeName, context)
      }

      val requestedNameIsQualified = requestedTypeName.contains(".")
      if (!requestedNameIsQualified) {
         val importedTypeName = imports.firstOrNull { it.typeName == requestedTypeName }
         if (importedTypeName != null) {
            return typeSystem.getTypeOrError(importedTypeName.parameterizedName, context)
         }
      }
      // Note: Use requestedTypeName, as qualifying it to the local namespace didn't help
      return CompilationError(context.start, ErrorMessages.unresolvedType(requestedTypeName), context.source().sourceName).left()
   }
   private fun resolveUserType(namespace: Namespace, classType: TaxiParser.ClassOrInterfaceTypeContext): Either<CompilationError, Type> {
      return resolveUserType(namespace, classType.Identifier().text(), importsInSource(classType), classType);
   }

   private fun importsInSource(context: ParserRuleContext): List<QualifiedName> {
      return tokens.importedTypeNamesInSource(context.source().normalizedSourceName)
   }


   private fun compileEnum(namespace: Namespace, typeName: String, ctx: TaxiParser.EnumDeclarationContext) {
      compileEnumValues(namespace, typeName, ctx.enumConstants())
         .mapLeft { errors -> throw CompilationException(errors) }
         .map { enumValues ->
            val annotations = collateAnnotations(ctx.annotation())
            val basePrimitive = deriveEnumBaseType(ctx, enumValues)
            val enumType = EnumType(typeName, EnumDefinition(
               enumValues,
               annotations,
               ctx.toCompilationUnit(),
               inheritsFrom = emptySet(),
               typeDoc = parseTypeDoc(ctx.typeDoc()),
               basePrimitive = basePrimitive
            ))
            typeSystem.register(enumType)
         }


   }

   private fun deriveEnumBaseType(ctx: TaxiParser.EnumDeclarationContext, enumValues: List<EnumValue>): PrimitiveType {
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
            parseSynonyms(namespace, enumConstant).map { synonyms ->
               synonymRegistry.registerSynonyms(qualifiedName, synonyms, enumConstant)
               EnumValue(name, value, qualifiedName, annotations, synonyms, parseTypeDoc(enumConstant.typeDoc()))
            }
         }.invertEitherList()
            .mapLeft { listOfLists: List<List<CompilationError>> -> listOfLists.flatten() }
      }
   }

   private fun parseSynonyms(namespace: Namespace, enumConstant: TaxiParser.EnumConstantContext): Either<List<CompilationError>, List<EnumValueQualifiedName>> {
      val declaredSynonyms = enumConstant.enumSynonymDeclaration()?.enumSynonymSingleDeclaration()?.let { listOf(it.qualifiedName()) }
         ?: enumConstant.enumSynonymDeclaration()?.enumSynonymDeclarationList()?.qualifiedName()
         ?: emptyList()
      return declaredSynonyms.map { synonym ->
         val (enumName, enumValueName) = Enums.splitEnumValueQualifiedName(synonym.Identifier().text())
         // TODO : I'm concerned this might cause stackoverflow / loops.
         // Will wait and see
         resolveUserType(namespace, enumName.parameterizedName, importsInSource(enumConstant), enumConstant)
            .flatMap { enumType ->
               if (enumType is EnumType) {
                  if (enumType.values.any { it.name == enumValueName }) {
                     Either.right(Enums.enumValue(enumType.toQualifiedName(), enumValueName))
                  } else {
                     Either.left(CompilationError(enumConstant.start, "$enumValueName is not defined on type ${enumType.qualifiedName}", enumConstant.source().normalizedSourceName))
                  }
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

      val typeName = qualify(namespace, typeRule.Identifier().text)
      val enum = typeSystem.getType(typeName) as EnumType
      return enum.addExtension(EnumExtension(enumValues, annotations, typeRule.toCompilationUnit(), typeDoc = typeDoc)).toCompilationError(typeRule.start)
   }

   private fun parseTypeDoc(content: TaxiParser.TypeDocContext?): String? {
      return parseTypeDoc(content?.source()?.content)
   }

   private fun compileDataSources() {
      val compiledDataSources = tokens.unparsedDataSources.map { (qualifiedName, dataSourceTokenPair) ->
         val (namespace, dataSourceToken) = dataSourceTokenPair
         parseType(namespace, dataSourceToken.typeType()).map { type ->
            val returnType = ArrayType(type, dataSourceToken.typeType().toCompilationUnit())
            val params = mapElementValuePairs(dataSourceToken.elementValuePairs())

            val path = params["path"]?.toString()
               ?: throw CompilationException(dataSourceToken.start, "path is required when declaring a fileResource", dataSourceToken.source().sourceName)
            val format = params["format"]?.toString()
               ?: throw CompilationException(dataSourceToken.start, "path is required when declaring a fileResource", dataSourceToken.source().sourceName)

            val annotations = collateAnnotations(dataSourceToken.annotation())
            FileDataSource(
               qualifiedName,
               path,
               format,
               returnType,
               dataSourceToken.sourceMapping().map { sourceMappingContext -> ColumnMapping(sourceMappingContext.Identifier().text, sourceMappingContext.columnDefinition().columnIndex().IntegerLiteral().text.toInt()) },
               annotations,
               listOf(dataSourceToken.toCompilationUnit())
            )
         }

      }.reportAndRemoveErrors()
      this.dataSources.addAll(compiledDataSources)
   }

   private fun compileServices() {
      val services = this.tokens.unparsedServices.map { (qualifiedName, serviceTokenPair) ->
         val (namespace, serviceToken) = serviceTokenPair
         val methods = serviceToken.serviceBody().serviceOperationDeclaration().map { operationDeclaration ->
            val signature = operationDeclaration.operationSignature()
            parseTypeOrVoid(namespace, signature.operationReturnType()).map { returnType ->
               val scope = operationDeclaration.operationScope()?.Identifier()?.text
               val operationParameters = signature.parameters().map { operationParameterContext ->
                  parseType(namespace, operationParameterContext.typeType()).map { paramType ->
                     Parameter(collateAnnotations(operationParameterContext.annotation()), paramType,
                        name = operationParameterContext.parameterName()?.Identifier()?.text,
                        constraints = mapConstraints(operationParameterContext.typeType(), paramType))
                  }
               }.reportAndRemoveErrors()

               Operation(name = signature.Identifier().text,
                  scope = scope,
                  annotations = collateAnnotations(operationDeclaration.annotation()),
                  parameters = operationParameters,
                  compilationUnits = listOf(operationDeclaration.toCompilationUnit()),
                  returnType = returnType,
                  contract = parseOperationContract(operationDeclaration, returnType))
            }
         }.reportAndRemoveErrors()
         Service(qualifiedName, methods, collateAnnotations(serviceToken.annotation()), listOf(serviceToken.toCompilationUnit()))
      }
      this.services.addAll(services)
   }

   private fun parseOperationContract(operationDeclaration: TaxiParser.ServiceOperationDeclarationContext, returnType: Type): OperationContract? {
      val signature = operationDeclaration.operationSignature()
      val constraintList = signature.operationReturnType()
         ?.typeType()
         ?.parameterConstraint()
         ?.parameterConstraintExpressionList()
         ?: return null

      val constraints = OperationConstraintConverter(constraintList, returnType).constraints()
      return OperationContract(returnType, constraints)
   }

   private fun mapConstraints(typeType: TaxiParser.TypeTypeContext, paramType: Type): List<Constraint> {
      if (typeType.parameterConstraint() == null) {
         return emptyList()
      }
      return OperationConstraintConverter(typeType.parameterConstraint()
         .parameterConstraintExpressionList(),
         paramType).constraints()
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

   fun typeResolver(namespace: String): TypeResolver {
      return { typeTypeContext -> parseType(namespace, typeTypeContext) }
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
