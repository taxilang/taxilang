package lang.taxi

import lang.taxi.services.*
import lang.taxi.types.*
import lang.taxi.types.Annotation
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

object Namespaces {
    const val DEFAULT_NAMESPACE = ""
}

data class CompilationError(val offendingToken: Token, val detailMessage: String?) {
    val line = offendingToken.line
    val char = offendingToken.charPositionInLine
}

class CompilationException(val errors: List<CompilationError>) : RuntimeException(errors.map { it.detailMessage }.filterNotNull().joinToString()) {
    constructor(offendingToken: Token, detailMessage: String?) : this(listOf(CompilationError(offendingToken, detailMessage)))
}

class Compiler(val inputs: List<CharStream>) {
    constructor(input: CharStream) : this(listOf(input))
    constructor(source: String, name:String = "<unknown>") : this(CharStreams.fromString(source,name))
    constructor(file: File) : this(CharStreams.fromPath(file.toPath()))

    companion object {
        fun fromStrings(sources: List<String>) = Compiler(sources.mapIndexed { index, source -> CharStreams.fromString(source, "StringSource-$index") })
    }

    fun compile(): TaxiDocument {
        // TODO : Can probably be smarter about this, and
        // stream the source, rather than returning a string from the
        // source provider
        val tokensCollection = inputs.map { input ->
            val listener = TokenCollator()
            val errorListener = CollectingErrorListener()
            val lexer = TaxiLexer(input)
            val parser = TaxiParser(CommonTokenStream(lexer))
            parser.addParseListener(listener)
            parser.addErrorListener(errorListener)
            val doc = parser.document()
            doc.exception?.let {
                throw CompilationException(it.offendingToken, it.message)
            }
            if (errorListener.errors.isNotEmpty())
                throw CompilationException(errorListener.errors)

            listener.tokens() // return..
        }
        val tokens = tokensCollection.reduce { acc, tokens -> acc.plus(tokens) }

        val builder = DocumentListener(tokens)
        return builder.buildTaxiDocument()
    }
}

internal class DocumentListener(val tokens: Tokens) {
    private val typeSystem = TypeSystem()
    private val services = mutableListOf<Service>()
    fun buildTaxiDocument(): TaxiDocument {
        compile()
        return TaxiDocument(typeSystem.typeList(), services)
    }


    private fun qualify(namespace: Namespace, name: String): String {
        if (name.contains("."))
        // This is already qualified
            return name
        if (namespace.isEmpty()) {
            return name
        }
        return "$namespace.$name"
    }

    private fun compile() {
        createEmptyTypes()
        compileTokens()
        compileTypeExtensions()
        compileServices()
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
            is TaxiParser.TypeAliasDeclarationContext -> compileTypeAlias(tokenName, tokenRule)
        // TODO : This is a bit broad - assuming that all typeType's that hit this
        // line will be a TypeAlias inline.  It could be a normal field declaration.
            is TaxiParser.TypeTypeContext -> compileInlineTypeAlias(namespace, tokenRule)
            else -> TODO("Not handled: $tokenRule")
        }
    }

    private fun compileTypeExtensions() {
        tokens.unparsedExtensions.forEach { (_, typeRule) ->
            when (typeRule) {
                is TaxiParser.TypeExtensionDeclarationContext -> compileTypeExtension(typeRule)
                else -> TODO("Not handled: $typeRule")
            }
        }
    }

    private fun compileTypeExtension(typeRule: TaxiParser.TypeExtensionDeclarationContext) {
        val typeName = typeRule.Identifier().text
        val type = typeSystem.getType(typeName) as ObjectType
        val annotations = collateAnnotations(typeRule.annotation())
        val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
            val fieldName = member.typeExtensionFieldDeclaration().Identifier().text
            val fieldAnnotations = collateAnnotations(member.annotation())
            FieldExtension(fieldName, fieldAnnotations)
        }
        type.extensions.add(ObjectTypeExtension(annotations, fieldExtensions,CompilationUnit.of(typeRule)))

    }

    private fun compileTypeAlias(tokenName: String, tokenRule: TaxiParser.TypeAliasDeclarationContext) {
        val qualifiedName = tokenRule.aliasedType().typeType().text
        val typePointedTo = typeSystem.getType(qualifiedName)
        val annotations = collateAnnotations(tokenRule.annotation())

        val definition = TypeAliasDefinition(typePointedTo, annotations, CompilationUnit.of(tokenRule))
        this.typeSystem.register(TypeAlias(tokenName, definition))
    }


    fun List<TerminalNode>.text(): String {
        return this.joinToString(".")
    }


    private fun compileType(namespace: Namespace, typeName: String, ctx: TaxiParser.TypeDeclarationContext) {
        val fields = ctx.typeBody().typeMemberDeclaration().map { member ->
            val fieldAnnotations = collateAnnotations(member.annotation())
            val type = parseType(namespace, member.fieldDeclaration().typeType())
            Field(name = member.fieldDeclaration().Identifier().text,
                    type = type,
                    nullable = member.fieldDeclaration().typeType().optionalType() != null,
                    annotations = fieldAnnotations,
                    constraints = mapConstraints(member.fieldDeclaration().typeType(), type)
            )
        }
        val annotations = collateAnnotations(ctx.annotation())
        val modifiers = parseModifiers(ctx.typeModifier())
        this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields, annotations, modifiers, CompilationUnit.of(ctx))))
    }

    private fun  parseModifiers(typeModifier: TaxiParser.TypeModifierContext?): List<Modifier> {
        typeModifier?.apply {
            return listOf(Modifier.fromToken(typeModifier.text))
        }
        return emptyList()
    }

    private fun collateAnnotations(annotations: List<TaxiParser.AnnotationContext>): List<Annotation> {
        return annotations.map { annotation ->
            val params: Map<String, Any> = mapAnnotationParams(annotation)
            val name = annotation.qualifiedName().text
            Annotation(name, params)
        }
    }

    private fun mapAnnotationParams(annotation: TaxiParser.AnnotationContext): Map<String, Any> {
        if (annotation.elementValue() != null) {
            return mapOf("value" to annotation.elementValue().literal().value())
        } else if (annotation.elementValuePairs() != null) {
            return annotation.elementValuePairs()!!.elementValuePair()?.map {
                it.Identifier().text to it.elementValue().literal()?.value()!!
            }?.toMap() ?: emptyMap()
        } else {
            // No params specified
            return emptyMap()
        }
    }

    private fun parseTypeOrVoid(namespace: Namespace, typeType: TaxiParser.TypeTypeContext?): Type {
        return if (typeType == null) {
            VoidType.VOID
        } else {
            parseType(namespace, typeType)
        }
    }

    private fun parseType(namespace: Namespace, typeType: TaxiParser.TypeTypeContext): Type {
        val type = when {
//            typeType.aliasedType() != null -> compileInlineTypeAlias(typeType)
            typeType.classOrInterfaceType() != null -> resolveUserType(namespace, typeType.classOrInterfaceType())
            typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text)
            else -> throw IllegalArgumentException()
        }
        if (typeType.listType() != null) {
            return ArrayType(type, CompilationUnit.of(typeType))
        } else {
            return type
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
        val typeAlias = TypeAlias(typeAliasName, TypeAliasDefinition(aliasedType, annotations, CompilationUnit.of(aliasTypeDefinition)))
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
        val enumValues = ctx.enumConstants().enumConstant().map { enumConstant ->
            val annotations = collateAnnotations(enumConstant.annotation())
            val value = enumConstant.Identifier().text
            EnumValue(value, annotations)
        }
        val annotations = collateAnnotations(ctx.annotation())
        val enumType = EnumType(typeName, EnumDefinition(enumValues, annotations, CompilationUnit.of(ctx)))
        typeSystem.register(enumType)
    }

    private fun compileServices() {
        val services = this.tokens.unparsedServices.map { (qualifiedName, serviceTokenPair) ->
            val (namespace, serviceToken) = serviceTokenPair
            val methods = serviceToken.serviceBody().serviceOperationDeclaration().map { operationDeclaration ->
                val signature = operationDeclaration.operationSignature()
                val returnType = parseTypeOrVoid(namespace, signature.typeType())
                Operation(name = signature.Identifier().text,
                        annotations = collateAnnotations(operationDeclaration.annotation()),
                        parameters = signature.operationParameterList().operationParameter().map {
                            val paramType = parseType(namespace, it.typeType())
                            Parameter(collateAnnotations(it.annotation()), paramType,
                                    name = it.parameterName()?.Identifier()?.text,
                                    constraints = mapConstraints(it.typeType(), paramType))
                        },
                        returnType = returnType,
                        contract = parseOperationContract(operationDeclaration, returnType)

                )
            }
            Service(qualifiedName, methods, collateAnnotations(serviceToken.annotation()), listOf(CompilationUnit.of(serviceToken)))
        }
        this.services.addAll(services)
    }

    private fun parseOperationContract(operationDeclaration: TaxiParser.ServiceOperationDeclarationContext, returnType: Type): OperationContract? {
        val signature = operationDeclaration.operationSignature()
        val constraintList = signature.typeType()
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


}

fun ParserRuleContext.source():SourceCode {
    val text = this.start.inputStream.getText(Interval(this.start.startIndex, this.stop.stopIndex))
    val origin = this.start.inputStream.sourceName
    return SourceCode(origin,text)
}
fun TaxiParser.LiteralContext.value(): Any {
    return when {
        this.StringLiteral() != null -> {
            // can be either ' or "
            val firstChar = this.StringLiteral().text.toCharArray()[0]
            this.StringLiteral().text.trim(firstChar)
        }
        this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
        this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
        else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
    }
}
