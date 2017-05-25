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
//    |   annotationTypeDeclaration
    ;


typeDeclaration
    :  'type' Identifier
//        ('extends' typeType)?
        typeBody
    ;

typeBody
    :   '{' typeMemberDeclaration* '}'
    ;

 typeMemberDeclaration
     :   fieldDeclaration
     ;

 fieldDeclaration
     :   Identifier ':' typeType
     ;

typeType
    :   classOrInterfaceType listType? optionalType?
    |   primitiveType listType? optionalType?
    ;

classOrInterfaceType
    :   Identifier /* typeArguments? ('.' Identifier typeArguments? )* */
    ;


enumDeclaration
    :   'enum' Identifier
        '{' enumConstants? '}'
    ;

enumConstants
    :   enumConstant (',' enumConstant)*
    ;

enumConstant
    :   annotation* Identifier
    ;

// Annotations
annotation
    :   '@' annotationName ( '(' ( elementValuePairs | elementValue )? ')' )?
    ;

annotationName : qualifiedName ;

elementValuePairs
    :   elementValuePair (',' elementValuePair)*
    ;

elementValuePair
    :   Identifier '=' elementValue
    ;

elementValue
    :   expression
    |   annotation
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

Identifier
    :   Letter LetterOrDigit*
    ;

primitiveType
    :   'Boolean'
    |   'String'
    |   'Int'
    |   'Double'
    ;

literal
    :   IntegerLiteral
    |   StringLiteral
    |   BooleanLiteral
    |   'null'
    ;

StringLiteral
    :   '"' StringCharacters? '"'
    ;

BooleanLiteral
    :   'true'
    |   'false'
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


BOOLEAN
   : 'true' | 'false'
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
