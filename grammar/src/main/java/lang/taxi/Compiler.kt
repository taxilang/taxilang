package lang.taxi

import lang.taxi.types.*
import lang.taxi.types.Annotation
import org.antlr.v4.runtime.*
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.nio.file.Path

data class CompilationError(val offendingToken: Token, val detailMessage: String?)
class CompilationException(val errors: List<CompilationError>) : RuntimeException(errors.map { it.detailMessage }.filterNotNull().joinToString()) {
   constructor(offendingToken: Token, detailMessage: String?) : this(listOf(CompilationError(offendingToken, detailMessage)))
}

class Compiler(val input: CharStream) {
   constructor(path: Path) : this(CharStreams.fromPath(path))
   constructor(source: String) : this(CharStreams.fromString(source))
   constructor(file: File) : this(CharStreams.fromPath(file.toPath()))

   companion object {
      fun main(args: Array<String>) {
      }
   }

   fun compile(): TaxiDocument {
      // TODO : Can probably be smarter about this, and
      // stream the source, rather than returning a string from the
      // source provider
      val lexer = TaxiLexer(input)
      val parser = TaxiParser(CommonTokenStream(lexer))

      val listener = DocumentListener()
      parser.addParseListener(listener)
      val doc = parser.document()
      doc.exception?.let {
         throw CompilationException(it.offendingToken, it.message)
      }
      val errors = listener.exceptions.map { (context, exception) ->
         CompilationError(context.start, exception.message)
      }
      if (errors.isNotEmpty())
         throw CompilationException(errors)

      return listener.buildTaxiDocument()
   }
}

private class DocumentListener : TaxiBaseListener() {
   val exceptions = mutableMapOf<ParserRuleContext, Exception>()
   private var namespace: String? = null
   private val typeSystem = TypeSystem()
   private val partiallyParsedTypes = mutableSetOf<String>()
   private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
   private val unparsedExtensions = mutableMapOf<String, ParserRuleContext>()
   fun buildTaxiDocument(): TaxiDocument {
      compile()
      return TaxiDocument(namespace, typeSystem.typeList())
   }

   private fun qualify(name: String): String {
      if (namespace == null) return name
      return "$namespace.$name"
   }

   override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
      collateExceptions(ctx)
      this.namespace = (ctx.payload as ParserRuleContext).children[1].text
      super.exitNamespaceDeclaration(ctx)
   }

   private fun compile() {
      createEmptyTypes()
      compileTokens()
      compileTypeExtensions()
   }

   private fun createEmptyTypes() {
      unparsedTypes.forEach { tokenName, token ->
         when (token) {
            is TaxiParser.EnumDeclarationContext -> typeSystem.register(EnumType.undefined(tokenName))
            is TaxiParser.TypeDeclarationContext -> typeSystem.register(ObjectType.undefined(tokenName))
         }
      }
   }

   private fun compileTokens() {
      unparsedTypes.forEach { tokenName, _ ->
         compileToken(tokenName)
      }
   }

   private fun compileToken(tokenName: String) {
      val tokenRule = unparsedTypes[tokenName]
      when (tokenRule) {
         is TaxiParser.TypeDeclarationContext -> compileType(tokenName, tokenRule)
         is TaxiParser.EnumDeclarationContext -> compileEnum(tokenName, tokenRule)
         else -> TODO("Not handled: $tokenRule")
      }
   }

   private fun compileTypeExtensions() {
      unparsedExtensions.forEach { typeName, typeRule ->
         when (typeRule) {
            is TaxiParser.TypeExtensionDeclarationContext -> compileTypeExtension(typeName, typeRule)
            else -> TODO("Not handled: $typeRule")
         }
      }
   }

   private fun compileTypeExtension(typeName: String, typeRule: TaxiParser.TypeExtensionDeclarationContext) {
      // TODO : handle extensions defined before types defined
      val type = typeSystem.getType(typeName) as ObjectType
      val annotations = collateAnnotations(typeRule.annotation())
      val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
         val fieldName = member.typeExtensionFieldDeclaration().Identifier().text
         val fieldAnnotations = collateAnnotations(member.annotation())
         FieldExtension(fieldName, fieldAnnotations)
      }
      type.extensions.add(ObjectTypeExtension(annotations, fieldExtensions))

   }

   override fun exitTypeExtensionDeclaration(ctx: TaxiParser.TypeExtensionDeclarationContext) {
      collateExceptions(ctx)
      val typeName = qualify(ctx.Identifier().text)
      unparsedExtensions.put(typeName, ctx)
      super.exitTypeExtensionDeclaration(ctx)
   }

   override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
      collateExceptions(ctx)
      val typeName = qualify(ctx.Identifier().text)
      unparsedTypes.put(typeName, ctx)
      super.exitTypeDeclaration(ctx)
   }

   private fun compileType(typeName: String, ctx: TaxiParser.TypeDeclarationContext) {
      val fields = ctx.typeBody().typeMemberDeclaration().map { member ->
         val fieldAnnotations = collateAnnotations(member.annotation())
         Field(name = member.fieldDeclaration().Identifier().text,
            type = parseType(member.fieldDeclaration().typeType()),
            nullable = member.fieldDeclaration().typeType().optionalType() != null,
            annotations = fieldAnnotations
         )
      }
      val annotations = collateAnnotations(ctx.annotation())
      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields, annotations)))
   }

   private fun collateAnnotations(annotations: List<TaxiParser.AnnotationContext>): List<Annotation> {
      return annotations.map { annotation ->
         val name = annotation.qualifiedName().text
         val parameters: Map<String, Any?> = annotation.elementValuePairs()?.elementValuePair()?.map {
            it.Identifier().text to it.elementValue().literal()?.value()
         }?.toMap() ?: emptyMap()
         Annotation(name, parameters)
      }
   }

   private fun parseType(typeType: TaxiParser.TypeTypeContext): Type {
      val type = when {
         typeType.classOrInterfaceType() != null -> resolveUserType(typeType.classOrInterfaceType())
         typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text)
         else -> throw IllegalArgumentException()
      }
      if (typeType.listType() != null) {
         return ArrayType(type)
      } else {
         return type
      }
   }

   private fun resolveUserType(classType: TaxiParser.ClassOrInterfaceTypeContext): Type {
      val typeName = qualify(classType.Identifier().text)
      if (typeSystem.contains(typeName)) {
         return typeSystem.getType(typeName)
      }

      if (unparsedTypes.contains(typeName)) {
         compileToken(typeName)
         return typeSystem.getType(typeName)
      }

      TODO("Throw a decent compilation error here -- the type was unknown")
   }

   override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
      collateExceptions(ctx)
      val name = qualify(ctx.Identifier().text)
      unparsedTypes.put(name, ctx)
      super.exitEnumDeclaration(ctx)
   }

   private fun collateExceptions(ctx: ParserRuleContext) {
      if (ctx.exception != null) {
         exceptions.put(ctx, ctx.exception)
      }
   }

   private fun compileEnum(typeName: String, ctx: TaxiParser.EnumDeclarationContext) {
      val enumValues = ctx.enumConstants().enumConstant().map { enumConstant ->
         val annotations = collateAnnotations(enumConstant.annotation())
         val value = enumConstant.Identifier().text
         EnumValue(value, annotations)
      }
      val annotations = collateAnnotations(ctx.annotation())
      val enumType = EnumType(typeName, EnumDefinition(enumValues, annotations))
      typeSystem.register(enumType)
   }

}

private fun TaxiParser.LiteralContext.value(): Any {
   return when {
      this.StringLiteral() != null -> this.StringLiteral().text.trim('"')
      this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
      this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
      else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
   }
}
