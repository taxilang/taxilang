package lang.taxi.lsp.highlighting

/**
 * This enum holds Taxi-specific concepts, mapped back to the TokenTypes that Monaco / VSCode understands
 */
enum class TaxiSemanticTokenType(val tokenType: String) {
   TYPE(SemanticTokenTypes.TYPE),
   MODEL(SemanticTokenTypes.CLASS),
   SERVICE(SemanticTokenTypes.INTERFACE),
   OPERATION(SemanticTokenTypes.FUNCTION),
   PARAMETER(SemanticTokenTypes.PARAMETER),

   NAMESPACE(SemanticTokenTypes.NAMESPACE),
   ENUM(SemanticTokenTypes.ENUM),
   ENUM_MEMBER(SemanticTokenTypes.ENUM_MEMBER),
   PROPERTY(SemanticTokenTypes.PROPERTY),
   VARIABLE(SemanticTokenTypes.VARIABLE),
   KEYWORD(SemanticTokenTypes.KEYWORD),
   STRING_LITERAL(SemanticTokenTypes.STRING),
   NUMERIC_LITERAL(SemanticTokenTypes.NUMBER),
   BOOLEAN_LITERAL(SemanticTokenTypes.NUMBER), // Not a typo - there's no better mapping that I can see
   OPERATOR(SemanticTokenTypes.OPERATOR),
   ANNOTATION(SemanticTokenTypes.ANNOTATION),

   ENTITY_NAME(CustomSemanticTokenTypes.ENTITY_NAME),
   PUNCTUATION(CustomSemanticTokenTypes.ENTITY_NAME),
}
