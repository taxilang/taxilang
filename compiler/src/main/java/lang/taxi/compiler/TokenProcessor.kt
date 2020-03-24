package lang.taxi.compiler

import lang.taxi.*
import lang.taxi.policies.*
import lang.taxi.services.*
import lang.taxi.types.*
import lang.taxi.types.Annotation
import org.antlr.v4.runtime.tree.TerminalNode

internal class TokenProcessor(val tokens: Tokens, importSources: List<TaxiDocument> = emptyList(), collectImports: Boolean = true) {

   constructor(tokens: Tokens, collectImports: Boolean) : this(tokens, emptyList(), collectImports)

   private val typeSystem: TypeSystem
   private val services = mutableListOf<Service>()
   private val policies = mutableListOf<Policy>()
   private val dataSources = mutableListOf<DataSource>()
   private val constraintValidator = ConstraintValidator()

   private val conditionalFieldSetProcessor = ConditionalFieldSetProcessor(this)

   init {
//        val importedTypes = mutableMapOf<String, Type>()
//        tokens.imports.forEach { (qualifiedName, token) ->
//
//        }
//        tokens.imports.map.mapTo(importedTypes) { (qualifiedName, token) ->
//            val importSource = importSources.firstOrNull { it.containsType(qualifiedName) }
//                    ?: throw CompilationException(token.start, "Cannot import $qualifiedName as it is not defined")
//            importSource.type(qualifiedName)
//        }
      val importedTypes = if (collectImports) {
         ImportedTypeCollator(tokens.imports, importSources).collect()
      } else {
         emptyList()
      }

      typeSystem = TypeSystem(importedTypes)
   }

   fun buildTaxiDocument(): TaxiDocument {
      compile()
      // TODO: Unsure if including the imported types here is a good iddea or not.
      val types = typeSystem.typeList(includeImportedTypes = true).toSet()
      return TaxiDocument(types, services.toSet(), policies.toSet(), dataSources.toSet())
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
         val typeAliasNames = typeCtx.typeBody()?.typeMemberDeclaration()?.mapNotNull { memberDeclaration ->
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

      // Some validations can't be performed at the time, because
      // they rely on a fully parsed document structure
      validateConstraints()
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
         is TaxiParser.EnumDeclarationContext -> compileEnum(tokenName, tokenRule)
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
         throw CompilationException(typeRule.start, "Cannot refine field $fieldName on $typeName to ${refinedType.qualifiedName} as it maps to ${refinedUnderlyingType.qualifiedName} which is incompatible with the existing type of ${originalType.qualifiedName}")
      }
      return refinedType
   }

   private fun compileTypeAlias(namespace: Namespace, tokenName: String, tokenRule: TaxiParser.TypeAliasDeclarationContext) {
      val qualifiedName = qualify(namespace, tokenRule.aliasedType().typeType())
      val aliasedType = parseType(namespace, tokenRule.aliasedType().typeType())
      val annotations = collateAnnotations(tokenRule.annotation())

      val definition = TypeAliasDefinition(aliasedType, annotations, tokenRule.toCompilationUnit(), typeDoc = parseTypeDoc(tokenRule.typeDoc()))
      this.typeSystem.register(TypeAlias(tokenName, definition))
   }


   fun List<TerminalNode>.text(): String {
      return this.joinToString(".")
   }


   private fun compileType(namespace: Namespace, typeName: String, ctx: TaxiParser.TypeDeclarationContext) {
      val typeBody = ctx.typeBody()

      val conditionalFieldStructures = typeBody?.conditionalTypeStructureDeclaration()?.map { conditionalFieldBlock ->
         conditionalFieldSetProcessor.compileConditionalFieldStructure(conditionalFieldBlock, namespace)
      } ?: emptyList()
      val fieldsWithConditions = conditionalFieldStructures.flatMap { it.fields }
      val fields = (typeBody?.typeMemberDeclaration()?.map { member ->
         compileField(member, namespace)
      } ?: emptyList()) + fieldsWithConditions
      val annotations = collateAnnotations(ctx.annotation())
      val modifiers = parseModifiers(ctx.typeModifier())
      val inherits = parseInheritance(namespace, ctx.listOfInheritedTypes())
      val typeDoc = parseTypeDoc(ctx.typeDoc()?.source()?.content)
      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields.toSet(), annotations.toSet(), modifiers, inherits, typeDoc, ctx.toCompilationUnit())))
   }

   internal fun compileField(member: TaxiParser.TypeMemberDeclarationContext, namespace: Namespace): Field {
      val fieldAnnotations = collateAnnotations(member.annotation())
      val accessor = compileAccessor(member.fieldDeclaration().accessor())
      val type = parseType(namespace, member.fieldDeclaration().typeType())
      return Field(
         name = unescape(member.fieldDeclaration().Identifier().text),
         type = type,
         nullable = member.fieldDeclaration().typeType().optionalType() != null,
         modifiers = mapFieldModifiers(member.fieldDeclaration().fieldModifier()),
         annotations = fieldAnnotations,
         constraints = mapConstraints(member.fieldDeclaration().typeType(), type),
         accessor = accessor
      )
   }

   private fun parseTypeDoc(content: String?): String? {
      if (content == null) {
         return null
      }
      return content.removeSurrounding("[[", "]]").trim()
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
            ?: throw CompilationException(fieldDeclaration.start, "Expected an accessor to be defined")
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
         parseType(namespace, typeTypeContext) as Type
      }.toSet()
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

   private fun parseTypeOrVoid(namespace: Namespace, returnType: TaxiParser.OperationReturnTypeContext?): Type {
      return if (returnType == null) {
         VoidType.VOID
      } else {
         parseType(namespace, returnType.typeType())
      }
   }

   internal fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Type {
      val type = when {
//            typeType.aliasedType() != null -> compileInlineTypeAlias(typeType)
         typeType.classOrInterfaceType() != null -> resolveUserType(namespace, typeType.classOrInterfaceType())
         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text)
         else -> throw IllegalArgumentException()
      }
      return if (typeType.listType() != null) {
         ArrayType(type, typeType.toCompilationUnit())
      } else {
         type
      }
   }

   /**
    * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
    * rather than those declared explicitly (type alias PersonFirstName as String)
    */
   private fun compileInlineTypeAlias(namespace: Namespace, aliasTypeDefinition: TaxiParser.TypeTypeContext): Type {
      val aliasedType = parseType(namespace, aliasTypeDefinition.aliasedType().typeType())
      val typeAliasName = qualify(namespace, aliasTypeDefinition.classOrInterfaceType().Identifier().text())
      // Annotations not supported on Inline type aliases
      val annotations = emptyList<Annotation>()
      val typeAlias = TypeAlias(typeAliasName, TypeAliasDefinition(aliasedType, annotations, aliasTypeDefinition.toCompilationUnit()))
      typeSystem.register(typeAlias)
      return typeAlias
   }

   private fun resolveUserType(namespace: Namespace, classType: TaxiParser.ClassOrInterfaceTypeContext): Type {
      val typeName = qualify(namespace, classType.Identifier().text())
      if (typeSystem.contains(typeName)) {
         return typeSystem.getType(typeName)
      }

      if (tokens.unparsedTypes.contains(typeName)) {
         compileToken(typeName)
         return typeSystem.getType(typeName)
      }
      throw CompilationException(classType.start, ErrorMessages.unresolvedType(typeName))
   }


   private fun compileEnum(typeName: String, ctx: TaxiParser.EnumDeclarationContext) {
      val enumValues = compileEnumValues(ctx.enumConstants())
      val annotations = collateAnnotations(ctx.annotation())
      val enumType = EnumType(typeName, EnumDefinition(
         enumValues,
         annotations,
         ctx.toCompilationUnit(),
         inheritsFrom = emptySet(),
         typeDoc = parseTypeDoc(ctx.typeDoc())
      ))
      typeSystem.register(enumType)
   }

   private fun compileEnumValues(enumConstants: TaxiParser.EnumConstantsContext?): List<EnumValue> {
      @Suppress("IfThenToElvis")
      return if (enumConstants == null) {
         emptyList()
      } else {
         enumConstants.enumConstant().map { enumConstant ->
            val annotations = collateAnnotations(enumConstant.annotation())
            val value = enumConstant.Identifier().text
            EnumValue(value, annotations, parseTypeDoc(enumConstant.typeDoc()))
         }
      }
   }


   private fun compileEnumExtension(namespace: Namespace, typeRule: TaxiParser.EnumExtensionDeclarationContext): CompilationError? {
      val enumValues = compileEnumValues(typeRule.enumConstants())
      val annotations = collateAnnotations(typeRule.annotation())
      val typeDoc = parseTypeDoc(typeRule.typeDoc())

      val typeName = qualify(namespace, typeRule.Identifier().text)
      val enum = typeSystem.getType(typeName) as EnumType
      return enum.addExtension(EnumDefinition(enumValues, annotations, typeRule.toCompilationUnit(), inheritsFrom = emptySet(), typeDoc = typeDoc)).toCompilationError(typeRule.start)
   }

   private fun parseTypeDoc(content: TaxiParser.TypeDocContext?): String? {
      return parseTypeDoc(content?.source()?.content)
   }

   private fun compileDataSources() {
      val compiledDataSources = tokens.unparsedDataSources.map { (qualifiedName, dataSourceTokenPair) ->
         val (namespace, dataSourceToken) = dataSourceTokenPair
         val returnType = ArrayType(parseType(namespace, dataSourceToken.typeType()), dataSourceToken.typeType().toCompilationUnit())
         val params = mapElementValuePairs(dataSourceToken.elementValuePairs())

         val path = params["path"]?.toString()
            ?: throw CompilationException(dataSourceToken.start, "path is required when declaring a fileResource")
         val format = params["format"]?.toString()
            ?: throw CompilationException(dataSourceToken.start, "path is required when declaring a fileResource")

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
      this.dataSources.addAll(compiledDataSources)
   }

   private fun compileServices() {
      val services = this.tokens.unparsedServices.map { (qualifiedName, serviceTokenPair) ->
         val (namespace, serviceToken) = serviceTokenPair
         val methods = serviceToken.serviceBody().serviceOperationDeclaration().map { operationDeclaration ->
            val signature = operationDeclaration.operationSignature()
            val returnType = parseTypeOrVoid(namespace, signature.operationReturnType())
            val scope = operationDeclaration.operationScope()?.Identifier()?.text
            Operation(name = signature.Identifier().text,
               scope = scope,
               annotations = collateAnnotations(operationDeclaration.annotation()),
               parameters = signature.parameters().map {
                  val paramType = parseType(namespace, it.typeType())
                  Parameter(collateAnnotations(it.annotation()), paramType,
                     name = it.parameterName()?.Identifier()?.text,
                     constraints = mapConstraints(it.typeType(), paramType))
               },
               compilationUnits = listOf(operationDeclaration.toCompilationUnit()),
               returnType = returnType,
               contract = parseOperationContract(operationDeclaration, returnType)

            )
         }
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

         val targetType = parseType(namespace, token.typeType())
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
         listOf(PolicyStatement(ElseCondition(), Instruction.parse(token.policyInstruction()), token.toCompilationUnit()))
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
         token.policyElse() != null -> ElseCondition() to Instruction.parse(token.policyElse().policyInstruction())
         else -> error("Invalid condition is neither a case nor an else")
      }
   }

   private fun compileCaseCondition(namespace: String, case: TaxiParser.PolicyCaseContext): Pair<Condition, Instruction> {
      val typeResolver = typeResolver(namespace)
      val condition = CaseCondition(
         Subject.parse(case.policyExpression(0), typeResolver),
         Operator.parse(case.policyOperator().text),
         Subject.parse(case.policyExpression(1), typeResolver)
      )
      val instruction = Instruction.parse(case.policyInstruction())
      return condition to instruction
   }

}
