package lang.taxi.lsp.utils

import lang.taxi.Compiler
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.ArrayMarkerContext
import lang.taxi.TaxiParser.OptionalTypeReferenceContext
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.searchUpForRule
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import org.antlr.v4.runtime.ParserRuleContext

fun QualifiedName.isPrimitiveType(): Boolean {
   return PrimitiveType.isPrimitiveType(this.fullyQualifiedName)
}

fun isFieldDeclaration(context: ParserRuleContext?): Boolean {
   return getFieldDeclaration(context) != null
}

fun getFieldDeclaration(context: ParserRuleContext?): TaxiParser.FieldTypeDeclarationContext? {
   return context?.searchUpForRule()
}

fun getFieldType(context: ParserRuleContext, compiler: Compiler): QualifiedName {
   val typeContext = when (context) {
      // The cursor is on the field name
      is TaxiParser.IdentifierContext -> context.searchUpForRule<TaxiParser.FieldDeclarationContext>()!!
         .fieldTypeDeclaration().optionalTypeReference().typeReference()

      is ArrayMarkerContext -> context.searchUpForRule<TypeReferenceContext>()!!
      else -> error("Unexpected token type: ${context::class.simpleName}")
   }

   return compiler.lookupTypeByName(typeContext)
}
