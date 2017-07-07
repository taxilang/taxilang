grammar Taxi;

// starting point for parsing a taxi file
document
    :   namespaceDeclaration? importDeclaration* toplevelObject* EOF
    ;

namespaceDeclaration
    :   'namespace' qualifiedName
    ;

importDeclaration
    :   'import' qualifiedName ('.' '*')? ';'
    ;

toplevelObject
    :   typeDeclaration
    |   enumDeclaration
    |   typeExtensionDeclaration
    |   typeAliasDeclaration
    |   serviceDeclaration
//    |   annotationTypeDeclaration
    ;


typeDeclaration
    :  annotation* 'type' Identifier
//        ('extends' typeType)?
        typeBody
    ;

typeBody
    :   '{' typeMemberDeclaration* '}'
    ;

 typeMemberDeclaration
     :   annotation* fieldDeclaration
     ;

 fieldDeclaration
     :   Identifier ':' typeType
     ;

typeType
    :   classOrInterfaceType listType? optionalType? aliasedType?
    |   primitiveType listType? optionalType?
    ;

classOrInterfaceType
    :   Identifier /* typeArguments? ('.' Identifier typeArguments? )* */
    ;


enumDeclaration
    :   annotation* 'enum' Identifier
        '{' enumConstants? '}'
    ;

enumConstants
    :   enumConstant (',' enumConstant)*
    ;

enumConstant
    :   annotation* Identifier
    ;
// type aliases
typeAliasDeclaration
    : annotation* 'type alias' Identifier aliasedType
    ;

aliasedType
   : 'as' typeType
   ;
// Annotations
annotation
    :   '@' qualifiedName ( '(' ( elementValuePairs | elementValue )? ')' )?
    ;

elementValuePairs
    :   elementValuePair (',' elementValuePair)*
    ;

elementValuePair
    :   Identifier '=' elementValue
    ;

elementValue
    :   literal
    |   annotation
    ;

serviceDeclaration
    :   annotation* 'service' Identifier serviceBody
    ;

serviceBody
    :   '{' serviceFunctionDeclaration* '}'
    ;

 serviceFunctionDeclaration
     :   annotation* 'fun' functionSignature
     ;

functionSignature
     :   annotation* Identifier '(' functionParameter* ')' ':' typeType
     ;

functionParameter
     :   annotation* typeType
     ;
expression
    :   primary
    ;

primary
    :   '(' expression ')'
//    |   'this'
//    |   'super'
    |   literal
    |   Identifier
//    |   typeType '.' 'class'
//    |   'void' '.' 'class'
//    |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
    ;

qualifiedName
    :   Identifier ('.' Identifier)*
    ;

primitiveType
    :   'Boolean'
    |   'String'
    |   'Int'
    |   'Double'
    |   'Decimal'
//    The "full-date" notation of RFC3339, namely yyyy-mm-dd. Does not support time or time zone-offset notation.
    |   'Date'
//    The "partial-time" notation of RFC3339, namely hh:mm:ss[.ff...]. Does not support date or time zone-offset notation.
    |   'Time'
// Combined date-only and time-only with a separator of "T", namely yyyy-mm-ddThh:mm:ss[.ff...]. Does not support a time zone offset.
    |   'DateTime'
// A timestamp, indicating an absolute point in time.  Includes timestamp.  Should be rfc3339 format.  (eg: 2016-02-28T16:41:41.090Z)
    |   'Instant'
    ;
// https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md#date
literal
    :   IntegerLiteral
    |   BooleanLiteral
    |   StringLiteral
    |   'null'
    ;

typeExtensionDeclaration
   :  annotation* 'type extension' Identifier typeExtensionBody
   ;

typeExtensionBody
    :   '{' typeExtensionMemberDeclaration* '}'
    ;

 typeExtensionMemberDeclaration
     :   annotation* typeExtensionFieldDeclaration
     ;

 typeExtensionFieldDeclaration
     :   Identifier
     ;

Identifier
    :   Letter LetterOrDigit*
    ;


StringLiteral
    :   '"' StringCharacters? '"'
    ;

BooleanLiteral
    :   'true' | 'false'
    ;

fragment
StringCharacters
    :   StringCharacter+
    ;

fragment
StringCharacter
    :   ~["\\]
    |   EscapeSequence
    ;

// §3.10.6 Escape Sequences for Character and String Literals

fragment
EscapeSequence
    :   '\\' [btnfr"'\\]
//    |   OctalEscape
//    |   UnicodeEscape
    ;


fragment
Letter
    :   [a-zA-Z$_] // these are the "java letters" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

fragment
LetterOrDigit
    :   [a-zA-Z0-9$_] // these are the "java letters or digits" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

IntegerLiteral
    :   DecimalNumeral /* IntegerTypeSuffix? */
    ;

fragment
DecimalNumeral
    :   '0'
    |   NonZeroDigit (Digits? | Underscores Digits)
    ;

fragment
Digits
    :   Digit (DigitOrUnderscore* Digit)?
    ;

fragment
Digit
    :   '0'
    |   NonZeroDigit
    ;

fragment
NonZeroDigit
    :   [1-9]
    ;

fragment
DigitOrUnderscore
    :   Digit
    |   '_'
    ;

fragment
Underscores
    :   '_'+
    ;

listType
   : '[]'
   ;

optionalType
   : '?'
   ;

NAME
   : [_A-Za-z] [_0-9A-Za-z]*
   ;


STRING
   : '"' ( ESC | ~ ["\\] )* '"'
   ;


fragment ESC
   : '\\' ( ["\\/bfnrt] | UNICODE )
   ;


fragment UNICODE
   : 'u' HEX HEX HEX HEX
   ;


fragment HEX
   : [0-9a-fA-F]
   ;


NUMBER
   : '-'? INT '.' [0-9]+ EXP? | '-'? INT EXP | '-'? INT
   ;


fragment INT
   : '0' | [1-9] [0-9]*
   ;

fragment EXP
   : [Ee] [+\-]? INT
   ;

//
// Whitespace and comments
//

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

COMMENT
    :   '/*' .*? '*/' -> channel(HIDDEN)
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> channel(HIDDEN)
    ;
