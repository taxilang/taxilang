grammar Taxi;

// starting point for parsing a taxi file
document
    :   (singleNamespaceDocument | multiNamespaceDocument)
    ;

singleNamespaceDocument
    :  importDeclaration* namespaceDeclaration? toplevelObject* EOF
    ;

multiNamespaceDocument
    : importDeclaration* namespaceBlock* EOF
    ;

importDeclaration
    :   'import' qualifiedName
    ;

namespaceDeclaration
    :   'namespace' qualifiedName
    ;

namespaceBlock
    :   'namespace' qualifiedName namespaceBody
    ;


namespaceBody
    : '{' toplevelObject* '}'
    ;

toplevelObject
    :   typeDeclaration
    |   enumDeclaration
    |   enumExtensionDeclaration
    |   typeExtensionDeclaration
    |   typeAliasDeclaration
    |   typeAliasExtensionDeclaration
    |   serviceDeclaration
    |   policyDeclaration
//    |   annotationTypeDeclaration
    ;

typeModifier
// A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
    : 'parameter'
    | 'closed'
    ;

// Typedoc is a special documentation block that wraps types.
// It's treated as plain text, but we'll eventually support doc tools
// that speak markdown.
// Comment markers are [[ .... ]], as this is less likely to generate clashes.
typeDoc
   : '[[' .*? ']]';

typeDeclaration
    :  typeDoc? annotation* typeModifier* 'type' Identifier
        ('inherits' listOfInheritedTypes)?
        typeBody
    ;

listOfInheritedTypes
    : typeType (',' typeType)*
    ;
typeBody
    :   '{' typeMemberDeclaration* '}'
    ;

 typeMemberDeclaration
     :   annotation* fieldDeclaration
     ;

 fieldModifier
    : 'closed'
    ;
 fieldDeclaration
     :   fieldModifier? Identifier ':' typeType accessor?
     ;

typeType
    :   classOrInterfaceType parameterConstraint? listType? optionalType? aliasedType?
    |   primitiveType parameterConstraint? listType? optionalType?
    ;

accessor : scalarAccessor | objectAccessor;

scalarAccessor
    : 'by' scalarAccessorExpression
    ;

scalarAccessorExpression
    : xpathAccessorDeclaration
    | jsonPathAccessorDeclaration
    ;

xpathAccessorDeclaration : 'xpath' '(' accessorExpression ')';
jsonPathAccessorDeclaration : 'jsonPath' '(' accessorExpression ')';

objectAccessor
    : '{' destructuredFieldDeclaration* '}'
    ;

destructuredFieldDeclaration
    : Identifier accessor
    ;
accessorExpression : StringLiteral;

classOrInterfaceType
    :   Identifier /* typeArguments? */ ('.' Identifier /* typeArguments? */ )*
    ;


enumDeclaration
    :    typeDoc? annotation* 'enum' classOrInterfaceType
        '{' enumConstants? '}'
    ;

enumConstants
    :   enumConstant (',' enumConstant)*
    ;

enumConstant
    :   typeDoc? annotation* Identifier
    ;

 enumExtensionDeclaration
    : typeDoc? annotation* 'enum extension' Identifier  ('{' enumConstants? '}')?
    ;

// type aliases
typeAliasDeclaration
    : typeDoc? annotation* 'type alias' Identifier aliasedType
    ;

aliasedType
   : 'as' typeType
   ;

typeAliasExtensionDeclaration
   : typeDoc? annotation* 'type alias extension' Identifier
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
    :   '{' serviceOperationDeclaration* '}'
    ;

 serviceOperationDeclaration
     :   annotation* operationScope? 'operation' operationSignature
     ;

operationSignature
     :   annotation* Identifier '(' operationParameterList? ')' operationReturnType?
     ;

operationScope : Identifier;

operationReturnType
    : ':' typeType
    ;
operationParameterList
    :   operationParameter (',' operationParameter)*
    ;

operationParameter
// Note that only one operationParameterConstraint can exist per parameter, but it can contain
// multiple expressions
     :   annotation* (parameterName)? typeType
     ;

// Parameter names are optional.
// But, they must be used to be referenced in return contracts
parameterName
    :   Identifier ':'
    ;

parameterConstraint
    :   '(' parameterConstraintExpressionList ')'
    ;


parameterConstraintExpressionList
    :  parameterConstraintExpression (',' parameterConstraintExpression)*
    ;

parameterConstraintExpression
    :  parameterExpectedValueConstraintExpression
    |  operationReturnValueOriginExpression
    ;

// The return value will have a relationship to a property
// received in an input (incl. nested properties)
operationReturnValueOriginExpression
    :  'from' qualifiedName
    ;

// A parameter will have a specific constnat value.
parameterExpectedValueConstraintExpression
    : Identifier '=' literal
    | Identifier '=' qualifiedName
    ;

policyDeclaration
    :  annotation* 'policy' policyIdentifier 'against' typeType '{' policyRuleSet* '}';

policyOperationType
    : Identifier;

policyRuleSet : policyOperationType policyScope? '{' (policyBody | policyInstruction) '}';

policyScope : 'internal' | 'external';


policyBody
    :   policyStatement*
    ;

policyIdentifier : Identifier;

policyStatement
    : policyCase | policyElse;

// TODO: Should consider revisiting this, so that operators are followed by valid tokens.
// eg: 'in' must be followed by an array.  We could enforce this at the language, to simplify in Vyne
policyCase
    : 'case' policyExpression policyOperator policyExpression '->' policyInstruction
    ;

policyElse
    : 'else' '->' policyInstruction
    ;
policyExpression
    : callerIdentifer
    | thisIdentifier
    | literalArray
    | literal;

callerIdentifer : 'caller' '.' typeType;
thisIdentifier : 'this' '.' typeType;

// TODO: Should consider revisiting this, so that operators are followed by valid tokens.
// eg: 'in' must be followed by an array.  We could enforce this at the language, to simplify in Vyne
policyOperator
    : '='
    | '!='
    | 'in'
    ;

literalArray
    : '[' literal (',' literal)* ']'
    ;

policyInstruction
    : policyInstructionEnum
    | policyFilterDeclaration
    ;

policyInstructionEnum
    : 'permit';

policyFilterDeclaration
    : 'filter' filterAttributeNameList?
    ;

filterAttributeNameList
    : '(' Identifier (',' Identifier)* ')'
    ;

// processors currently disabled
// https://gitlab.com/vyne/vyne/issues/52
//policyProcessorDeclaration
//    : 'process' 'using' qualifiedName policyProcessorParameterList?
//    ;

//policyProcessorParameterList
//    : '(' policyParameter (',' policyParameter)* ')'
//    ;

//policyParameter
//    : literal | literalArray;
//

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

listType
   : '[]'
   ;

optionalType
   : '?'
   ;

primitiveType
    : primitiveTypeName
    | 'lang.taxi.' primitiveTypeName
    ;

primitiveTypeName
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
    |  'Any'
    ;
// https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md#date
literal
    :   IntegerLiteral
    |   BooleanLiteral
    |   StringLiteral
    |   'null'
    ;

typeExtensionDeclaration
   :  typeDoc? annotation* 'type extension' Identifier typeExtensionBody
   ;

typeExtensionBody
    :   '{' typeExtensionMemberDeclaration* '}'
    ;

typeExtensionMemberDeclaration
    :   annotation* typeExtensionFieldDeclaration
    ;

typeExtensionFieldDeclaration
    :   Identifier typeExtensionFieldTypeRefinement?
    ;

typeExtensionFieldTypeRefinement
    : ':' typeType
    ;

Identifier
    :   Letter LetterOrDigit*
    | '`' ~('`')+ '`'
    ;


StringLiteral
    :   '"' StringCharacters? '"'
    |   '\'' StringCharacters? '\''
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
