package lang.taxi.lsp.highlighting

/**
 * These are the token types that VSCode understand.
 * They're not taxi-specific.
 *
 * Use TaxiTokenTypes instead
 */
object SemanticTokenTypes {
   const val NAMESPACE = "namespace"      // For namespace declarations
   const val TYPE = "type"               // For type declarations and references
   const val CLASS = "class"             // For model declarations
   const val INTERFACE = "interface"      // For service declarations
   const val ENUM = "enum"               // For enum declarations
   const val ENUM_MEMBER = "enumMember"   // For enum values
   const val FUNCTION = "function"        // For operations and functions
   const val PARAMETER = "parameter"      // For operation/function parameters
   const val PROPERTY = "property"        // For model fields and properties
   const val VARIABLE = "variable"        // For variables in expressions
   const val KEYWORD = "keyword"          // For language keywords (inherits, as, by, etc.)
   const val STRING = "string"            // For string literals
   const val NUMBER = "number"            // For numeric literals
   const val OPERATOR = "operator"        // For operators (+, -, *, /, etc.)
   const val ANNOTATION = "annotation"    // For annotations (@Id, @HttpOperation, etc.)

   // Create a list of all token types
   val ALL = listOf(
      NAMESPACE,
      TYPE,
      CLASS,
      INTERFACE,
      ENUM,
      ENUM_MEMBER,
      FUNCTION,
      PARAMETER,
      PROPERTY,
      VARIABLE,
      KEYWORD,
      STRING,
      NUMBER,
      OPERATOR,
      ANNOTATION
   )
}

/**
 * These are semantic token types not provided by the default VSCode set,
 * but are custom to Taxi. They must be mapped in the package.json
 * to a TextMate scope
 *
 * see: https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#custom-textmate-scope-mappings
 *
 * See here for a list of Textmate scopes:
 * https://macromates.com/manual/en/language_grammars
 *
 * Often, it's best to use the scope inspector (https://code.visualstudio.com/api/language-extensions/syntax-highlight-guide#scope-inspector)
 * in another language, to see the corresponding textmate scope.
 *
 * Only use these when there isn't a good match in SemanticTokenTypes, and the token type
 * is sufficiently different to justify the creation of a new one.
 */
object CustomSemanticTokenTypes {
   const val ENTITY_NAME = "entityName"
   const val PUNCTUATION = "punctuation"
}

object SemanticTokenModifiers {
   const val DECLARATION = "declaration"   // For type/model/enum declarations
   const val DEFINITION = "definition"     // For field/property definitions
   const val READONLY = "readonly"         // For closed models
   const val STATIC = "static"            // For type aliases
   const val ABSTRACT = "abstract"        // For parameter models
   const val DEPRECATED = "deprecated"     // For deprecated features
   const val MODIFICATION = "modification" // For type extensions
   const val DOCUMENTATION = "documentation" // For doc comments
   const val DEFAULT_LIBRARY = "defaultLibrary" // For built-in types

   // Create a list of all token modifiers
   val ALL = listOf(
      DECLARATION,
      DEFINITION,
      READONLY,
      STATIC,
      ABSTRACT,
      DEPRECATED,
      MODIFICATION,
      DOCUMENTATION,
      DEFAULT_LIBRARY
   )
}
