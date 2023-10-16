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
    |   functionDeclaration
    |   annotationTypeDeclaration
    |   query
    ;

typeModifier
// A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
    : 'parameter'
    | 'closed'
    ;

typeKind : 'type' | 'model';

typeDeclaration
    :  typeDoc? annotation* typeModifier* typeKind identifier
         typeArguments?
        ('inherits' listOfInheritedTypes)?
        (typeBody | expressionTypeDeclaration)?
    ;

listOfInheritedTypes
    : typeReference (',' typeReference)*
    ;

spreadOperatorDeclaration
    : SPREAD_OPERATOR (K_Except '{' identifier ( ',' identifier )* '}')?
    ;

typeBody
:   '{' (typeMemberDeclaration (',')? )* spreadOperatorDeclaration? '}'
    ;

typeMemberDeclaration
     :   typeDoc? annotation* fieldDeclaration
     ;

expressionTypeDeclaration : 'by' expressionGroup*;

// (A,B) -> C
// Used in functions:
// declare function <T,A> sum(T[], (T) -> A):A
// Note - this is used when the lambda is declared, not when
// it's used in a function as an expression.
// eg:
// given the sum example above, it's usage would be:
// model Output {
// total : Int by sum(this.transactions, (Transaction) -> Cost)
//}
// In that example, the sum(this.transactions, (Transaction) -> Cost) is
// an exmpression, not a lambdaSignature
lambdaSignature: expressionInputs typeReference;


expressionInputs: '(' expressionInput (',' expressionInput)* ')' '->';
expressionInput: (identifier ':')? typeReference;


// Added for expression types.
// However, I suspect with a few modifications e can simplify
// all expressions to
// this group (fields, queries, etc.).
// This definition is based off of this:
// https://github.com/antlr/grammars-v4/blob/master/arithmetic/arithmetic.g4
// which ensures order-of-precedence and supports grouped / parenthesis
// expressions
expressionGroup:
   expressionGroup POW expressionGroup
   | expressionGroup (MULT | DIV) expressionGroup
   | expressionGroup (PLUS | MINUS) expressionGroup
   | LPAREN expressionGroup RPAREN
   | (PLUS | MINUS)* expressionAtom
   // The below is added for lambdas, but not sure order of precedence
   // is correct. TBD.
   | expressionGroup comp_operator expressionGroup
   | expressionGroup COALESCE expressionGroup
   | expressionGroup LOGICAL_AND expressionGroup
   | expressionGroup LOGICAL_OR expressionGroup
   | whenBlock
   // Inputs go last, so that when parsing lambdas, the inputs are the LHS and everything remainin goes RHS.
   // Might not work for nested lambdas, if that's a thing.
   | expressionInputs expressionGroup
   | expressionGroup typeProjection;

// readFunction before typeType to avoid functons being identified
// as types
// 1-Oct: Tried collapsing scalarAccessorExpression into this, but it caused errors.
// Would like to simplify...
expressionAtom: functionCall | typeReference | fieldReferenceSelector | modelAttributeTypeReference | literal | anonymousTypeDefinition;

//scalarAccessorExpression
  //    : xpathAccessorDeclaration
  //    | jsonPathAccessorDeclaration
  //    | columnDefinition
  //    | defaultDefinition
  //    | readFunction
  //    | readExpression
  //    | byFieldSourceExpression
  //    | collectionProjectionExpression
  //   | conditionalTypeConditionDeclaration

annotationTypeDeclaration
   : typeDoc? annotation* 'annotation' identifier annotationTypeBody?;

annotationTypeBody: '{' typeMemberDeclaration* '}';



// Deprecated - use expressionGroups instead
//fieldExpression
//   : '(' propertyToParameterConstraintLhs arithmaticOperator propertyToParameterConstraintLhs ')'
//   ;
whenBlock:
   K_When ('(' expressionGroup ')')? '{'
   whenCaseDeclaration*
   '}';

// field references must be prefixed by this. -- ie., this.firstName
// this is to disambiguoate lookups by type -- ie., Name
//
// Note: Have had to relax the requirement for propertyFieldNameQualifier
// to be mandatory, as this created bacwards comapatbility issues
// in when() clauses
//
// Update: In the type expressions feature branch
// I've remove the relaxed requirement, re-enforcing that
// field refereces must be prefixed.
// Otherwise, these no lexer difference between
// a fieldReferenceSelector (not permitted in expression types)
// and a typeReferenceSelector (which is permitted)
fieldReferenceSelector: propertyFieldNameQualifier qualifiedName;
typeReferenceSelector: typeReference;

whenCaseDeclaration:
   caseDeclarationMatchExpression '->' (  /*caseFieldAssignmentBlock |  */  expressionGroup | scalarAccessorExpression | modelAttributeTypeReference);

caseDeclarationMatchExpression: // when( ... ) {
   expressionGroup |
   K_Else;

caseFieldAssigningDeclaration :  // dealtAmount ...  (could be either a destructirng block, or an assignment)
   identifier (
      caseFieldDestructuredAssignment | // dealtAmount ( ...
      ( EQ caseScalarAssigningDeclaration ) | // dealtAmount = ccy1Amount | dealtAmount = 'foo'
      // TODO : How do we model Enum assignments here?
      // .. some enum assignment ..
      accessor
   );

caseScalarAssigningDeclaration:
   expressionGroup | scalarAccessorExpression;

caseFieldDestructuredAssignment :  // dealtAmount ( ... )
     '(' caseFieldAssigningDeclaration* ')';

fieldModifier
   : 'closed'
   ;
fieldDeclaration
  :   fieldModifier? identifier (':' (anonymousTypeDefinition | fieldTypeDeclaration | expressionGroup |  modelAttributeTypeReference))? typeProjection?
  ;

// Used in queries to scope projection of collections.
// eg:
//findAll { OrderTransaction[] } as {
//   items: Thing[] by [OrderItem[]]
// }[]
collectionProjectionExpression: '[' typeReference projectionScopeDefinition? ']' ;

projectionScopeDefinition: 'with' '(' scalarAccessorExpression (',' scalarAccessorExpression)*  ')';

// Used to describe navigation from one entity to another
// Eg from Type to Property Type (Person::FirstName)
// Or from Service to Operation (PersonService::findAllPeople)
memberReference: typeReference '::' typeReference;

// A type reference that refers to the attribute on a model.
// eg:  firstName : Person::FirstName.
// Only meaningful within views.
// Deprecated, prefer memberReference instead.
modelAttributeTypeReference: typeReference '::' typeReference |
   LPAREN typeReference '::' typeReference RPAREN arrayMarker;


// fieldType usages allow richer syntax with additional features like
// inline type definitions, optionality, aliases and accessors.
// Other type usage sites are not as flexible (eg., return type of an operation)
fieldTypeDeclaration: (nullableTypeReference parameterConstraint?)? (aliasedType? | inlineInheritedType?)? accessor?;

//typeReference : qualifiedName typeArguments? arrayMarker? optionalType?;


typeReference
    :   qualifiedName typeArguments? arrayMarker?;
    //

// Use in call sites where optional types are permitted
nullableTypeReference : typeReference Nullable?;

accessor
// by is deprecated, use "="
    : ('by' | '=') scalarAccessorExpression
    ;

scalarAccessorExpression
    : xpathAccessorDeclaration
    | jsonPathAccessorDeclaration
    | columnDefinition
//    | defaultDefinition
//    | readFunction
    | expressionGroup
    | byFieldSourceExpression
//    | collectionProjectionExpression
//    | conditionalTypeConditionDeclaration
    ;

// Required for Query based Anonymous type definitions like:
// {
//               traderEmail: UserEmail (by this.traderId)
// }
//
byFieldSourceExpression:  typeReference '['  StringLiteral  ']';
xpathAccessorDeclaration : 'xpath' '(' StringLiteral ')';
jsonPathAccessorDeclaration : 'jsonPath' '(' StringLiteral ')';


// Deprecating and removing this.
// It was never used, and is confusing
//objectAccessor
//    : '{' destructuredFieldDeclaration* '}'
//    ;
//
//destructuredFieldDeclaration
//    : identifier accessor
//    ;

//accessorExpression : StringLiteral;

typeArguments: '<' typeReference (',' typeReference)* '>';

// A "lenient" enum will match on case insensitive values
enumDeclaration
    :    typeDoc? annotation* lenientKeyword? 'enum' qualifiedName
         (('inherits' enumInheritedType) | ('{' enumConstants? '}'))
    ;

enumInheritedType
    : typeReference
    ;

enumConstants
    :   enumConstant (',' enumConstant)*
    ;

enumConstant
    :   typeDoc? annotation*  defaultKeyword? identifier enumValue? enumSynonymDeclaration?
    ;

enumValue
   : '(' literal ')'
   ;

enumSynonymDeclaration
   : 'synonym' 'of' ( enumSynonymSingleDeclaration | enumSynonymDeclarationList)
   ;
enumSynonymSingleDeclaration : qualifiedName ;
enumSynonymDeclarationList : '[' qualifiedName (',' qualifiedName)* ']'
   ;
 enumExtensionDeclaration
    : typeDoc? annotation* 'enum extension' identifier  ('{' enumConstantExtensions? '}')?
    ;

enumConstantExtensions
    :   enumConstantExtension (',' enumConstantExtension)*
    ;

enumConstantExtension
   : typeDoc? annotation* identifier enumSynonymDeclaration?
   ;

// type aliases
typeAliasDeclaration
    : typeDoc? annotation* 'type alias' identifier aliasedType
    ;

aliasedType
   : 'as' typeReference
   ;

inlineInheritedType
   : 'inherits' typeReference
   ;

typeAliasExtensionDeclaration
   : typeDoc? annotation* 'type alias extension' identifier
   ;
// Annotations
annotation
    :   '@' qualifiedName ( '(' ( elementValuePairs | elementValue )? ')' )?
    ;


elementValuePairs
    :   elementValuePair (',' elementValuePair?)* // permitting trailing commas make the grammar easier to parse
    ;

elementValuePair
    :  identifier '=' elementValue
    ;

elementValue
    :   literal
    |    qualifiedName // Support enum references within annotations
    |   annotation
    ;

serviceDeclaration
    : typeDoc? annotation* 'service' identifier serviceBody
    ;

serviceBody
    :   '{' lineageDeclaration? serviceBodyMember* '}'
    ;
serviceBodyMember : serviceOperationDeclaration | queryOperationDeclaration | tableDeclaration | streamDeclaration;
// Querying
queryOperationDeclaration
   :  typeDoc? annotation* queryGrammarName 'query' identifier '(' operationParameterList ')' ':' typeReference
      'with' 'capabilities' '{' queryOperationCapabilities '}';

queryGrammarName : identifier;
queryOperationCapabilities: (queryOperationCapability (',' queryOperationCapability)*);

queryOperationCapability:
   queryFilterCapability | identifier;

queryFilterCapability: 'filter'( '(' filterCapability (',' filterCapability)* ')');

filterCapability: EQ | NQ | IN | LIKE | GT | GE | LT | LE;

tableDeclaration: typeDoc? annotation* K_Table identifier ':' typeReference;
streamDeclaration: typeDoc? annotation* K_Stream identifier ':' typeReference;

lineageDeclaration
      : typeDoc? annotation* 'lineage' lineageBody;

lineageBody
      : '{' lineageBodyMember* '}';

lineageBodyMember
      : consumesBody | storesBody;

consumesBody: 'consumes' 'operation' qualifiedName;

storesBody: 'stores' qualifiedName;


serviceOperationDeclaration
     : typeDoc? annotation* operationScope? 'operation'  operationSignature
     ;

operationSignature
     :   annotation* identifier  '(' operationParameterList? ')' operationReturnType?
     ;

operationScope : identifier;

operationReturnType
    : ':' typeReference ('(' parameterConstraintExpressionList ')')?
    ;

 // typeReference
     //    :   qualifiedName typeArguments? arrayMarker? optionalType? parameterConstraint? (aliasedType? | inlineInheritedType?)?

operationParameterList
    :   operationParameter (',' operationParameter)*
    ;


operationParameter
// Note that only one operationParameterConstraint can exist per parameter, but it can contain
// multiple expressions
     :   typeDoc? annotation* (parameterName)? ((nullableTypeReference ( '(' parameterConstraintExpressionList ')')?  varargMarker?) | lambdaSignature)
     ;

varargMarker: '...';
// Parameter names are optional.
// But, they must be used to be referenced in return contracts
parameterName
    :   identifier ':'
    ;

parameterConstraint
    :   '('expressionGroup? ')'
//    |   '(' parameterConstraintExpressionList ')'
//    |   '(' temporalFormatList ')'
    ;


// We're deprecating this... use expression groups where possible.
parameterConstraintExpressionList
    :  parameterConstraintExpression (',' parameterConstraintExpression)*
    ;

// Deprecated - use expression groups where possible
parameterConstraintExpression
    :  propertyToParameterConstraintExpression
    |  operationReturnValueOriginExpression
    |  propertyFormatExpression
    ;

// First impl.  This will get richer (',' StringLiteral)*
propertyFormatExpression :
   '@format' '=' StringLiteral;

//temporalFormatList :
//   ('@format' '=' '[' StringLiteral (',' StringLiteral)* ']')? ','? (instantOffsetExpression)?
//   ;

//instantOffsetExpression :
//   '@offset' '=' IntegerLiteral;

// The return value will have a relationship to a property
// received in an input (incl. nested properties)
operationReturnValueOriginExpression
    :  'from' qualifiedName
    ;

// Deprecation warning: We're gonna deprecate this and find a way to just use normal expressions.
//
// A parameter will a value that matches a specified expression
// operation convertCurrency(request : ConversionRequest) : Money( this.currency = request.target )
// Models a constraint against an attribute on the type (generally return type).
// The attribute is identified by EITHER
// - it's name -- using this.fieldName
// - it's type (preferred) using TheTypeName
// The qualifiedName here is used to represent a path to the attribute (this.currency)
// We could've just used identifier here, but we'd like to support nested paths
//
// We're deprecating this... use expression groups where possible.
propertyToParameterConstraintExpression
   : propertyToParameterConstraintLhs comparisonOperator propertyToParameterConstraintRhs;

propertyToParameterConstraintLhs : (propertyFieldNameQualifier? qualifiedName)? | modelAttributeTypeReference?;
propertyToParameterConstraintRhs : (literal | qualifiedName);

propertyFieldNameQualifier : 'this' '.';

comp_operator : GT
              | GE
              | LT
              | LE
              | EQ
              | NQ
              ;


comparisonOperator
   : '=='
   | '>'
   | '>='
   | '<='
   | '<'
   | '!='
   ;

policyDeclaration
    :  annotation* 'policy' policyIdentifier 'against' typeReference '{' policyRuleSet* '}';

policyOperationType
    : identifier;

policyRuleSet : policyOperationType policyScope? '{' (policyBody | policyInstruction) '}';

policyScope : 'internal' | 'external';


policyBody
    :   policyStatement*
    ;

policyIdentifier : identifier;

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


callerIdentifer : 'caller' '.' typeReference;
thisIdentifier : 'this' '.' typeReference;

// TODO: Should consider revisiting this, so that operators are followed by valid tokens.
// eg: 'in' must be followed by an array.  We could enforce this at the language, to simplify in Vyne
policyOperator
    : EQ
    | NQ
    | IN
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
    : '(' identifier (',' identifier)* ')'
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

columnDefinition : 'column' '(' columnIndex ')' ;

// qualifiedName here is to reference enums
//defaultDefinition: 'default' '(' (literal | qualifiedName) ')';

// "declare function" borrowed from typescript.
// Note that taxi supports declaring a function, but won't provide
// an implementation of it.  That'll be down to individual libraries
// Note - intentional decision to enforce these functions to return something,
// rather than permitting void return types.
// This is because in a mapping declaration, functions really only have purpose if
// they return things.
functionDeclaration: typeDoc? 'declare' (functionModifiers)? 'function' typeArguments? qualifiedName '(' operationParameterList? ')' ':' typeReference;

functionModifiers: 'query';


// Could be MyType( foo == bar ), or myFunction( param1, param1 )
functionCall: qualifiedName '(' argumentList? ')';


// A list of arguments passed into a function call
// Permits trailing commas.  foo(a, )
// This seems to clarify the grammar, such that everything inside the parenthesis
// is an argument. Otherwise, we were finding the grammar parsed trailing commas
// as an addiitonal type member declaration.
argumentList
    : argument  (',' argument?)* // allowing trailing commas helps clarify the grammar
    ;

argument: literal |  scalarAccessorExpression | fieldReferenceSelector | typeReferenceSelector | modelAttributeTypeReference | expressionGroup;

columnIndex : IntegerLiteral | StringLiteral;

expression
    :   '(' expression ')'
    |   literal
    |   identifier;

qualifiedName
    :   identifier ('.' identifier)*
    ;

arrayMarker
   : '[]'
   ;

Nullable : '?';

//primitiveType
//    : primitiveTypeName
//    | 'lang.taxi.' primitiveTypeName
//    ;
//
//primitiveTypeName
//    :   'Boolean'
//    |   'String'
//    |   'Int'
//    |   'Double'
//    |   'Decimal'
////    The "full-date" notation of RFC3339, namely yyyy-mm-dd. Does not support time or time zone-offset notation.
//    |   'Date'
////    The "partial-time" notation of RFC3339, namely hh:mm:ss[.ff...]. Does not support date or time zone-offset notation.
//    |   'Time'
//// Combined date-only and time-only with a separator of "T", namely yyyy-mm-ddThh:mm:ss[.ff...]. Does not support a time zone offset.
//    |   'DateTime'
//// A timestamp, indicating an absolute point in time.  Includes timestamp.  Should be rfc3339 format.  (eg: 2016-02-28T16:41:41.090Z)
//    |   'Instant'
//    |  'Any'
//    ;

// https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md#date
literal
    :   IntegerLiteral
    |   DecimalLiteral
    |   BooleanLiteral
    |   StringLiteral
    |   'null'
    ;

typeExtensionDeclaration
   :  typeDoc? annotation* 'type extension' identifier typeExtensionBody
   ;

typeExtensionBody
    :   '{' typeExtensionMemberDeclaration* '}'
    ;

typeExtensionMemberDeclaration
    :   annotation* typeExtensionFieldDeclaration
    ;

typeExtensionFieldDeclaration
    :   identifier typeExtensionFieldTypeRefinement?
    ;

typeExtensionFieldTypeRefinement
    : ':' typeReference
    ;

//constantDeclaration : 'by'  defaultDefinition;

// Typedoc is a special documentation block that wraps types.
// It's treated as plain text, but we'll eventually support doc tools
// that speak markdown.
// Comment markers are [[ .... ]], as this is less likely to generate clashes.
typeDoc : DOCUMENTATION;
// : '[[' ('//' |  ~']]' | '"' | '\'')* ']]';


lenientKeyword: 'lenient';
defaultKeyword: 'default';

/*
 * Taxi QL
 */

query: namedQuery | anonymousQuery;

namedQuery: typeDoc? annotation* queryName '{' queryBody '}';
anonymousQuery: queryBody;

queryName: 'query' identifier queryParameters?;

queryParameters: '(' queryParamList ')';

queryParamList: queryParam (',' queryParam)*;

queryParam: annotation* identifier ':' typeReference;

queryDirective: K_Stream | K_Find | K_Map;
findDirective: K_Find;

givenBlock : 'given' '{' factList '}';

factList : fact (',' fact)*;


// MP: 04-Sep-23: Passing the value used to be optional.
// What was the use-case for this?
// Improving strictness around given blocks that refer to variables from queries,
// by clarifying the syntax. (factDeclaration vs variableName)
factDeclaration : (variableName ':')? typeReference '=' value;
fact: factDeclaration | variableName;

// TODO : We really should support expressions here.
value : objectValue | valueArray | literal;

objectValue: '{' objectField (',' objectField)* '}';
objectField : identifier ':' value;
valueArray: '[' value? (',' value)* ']';

variableName: identifier;

queryBody:
   typeDoc? annotation*
   givenBlock?
	queryOrMutation;

// A query body can contain EITHER:
// - a query followed by an optional mutation
// - OR a mutation on its own
// but it must contain one.
queryOrMutation:
   (queryDirective ( ('{' queryTypeList  '}') | anonymousTypeDefinition ) typeProjection? mutation?) |
   mutation;


queryTypeList: fieldTypeDeclaration (',' fieldTypeDeclaration)*;

typeProjection: 'as' (typeReference | expressionInputs? anonymousTypeDefinition);
//as {
//    orderId // if orderId is defined on the Order type, then the type is inferrable
//    productId: ProductId // Discovered, using something in the query context, it's up to Vyne to decide how.
//    traderEmail : EmailAddress(by this.traderUtCode)
//    salesPerson {
//        firstName : FirstName
//        lastName : LastName
//    }(by this.salesUtCode)
//}
anonymousTypeDefinition: annotation* typeBody arrayMarker? accessor? parameterConstraint?;

mutation: K_Call memberReference;

NOT_IN: 'not in';
IN: 'in';
LIKE: 'like';
AND : 'and' ;
OR  : 'or' ;

// Must come before Identifier, to capture booleans correctly
BooleanLiteral
    :   TRUE | FALSE
    ;

// Identifiers define tokens that name things. Listing `K_xxx` keywords here ensures that users can  define field
// names, operations and so on with words that are reserved in some context.

identifier:
   K_Table | K_Stream | K_Find | K_Map | K_Except | K_Call | IdentifierToken;

K_Find: 'find';

// map is the same as find, but it expects an array as an input,
// and then iterates on each member of the array performing a find.
// This is an experiment.
// Supported by:
// - It's currnetly not explict how the query engine is supposed to iterate
//   collections.
//      find { Foo[] } as { a : Something }[]
//   is a mapping operation, but feels valid as it's (A[] -> B[]).
//   Somehow when we map we want to go via another type.
//   eg:
//      given { Film[] } find { Foo[] } as { .... }
//   This is confusing, and amgiuous in terms of how to resolve
//   services to call for transformation.
//   eg:  Should we iterate Film[] to perform ( Film - > Foo )
//   or should we find a transformation service that goes (Film[] -> Foo[])?
//   This amgiguiuty isn't good.
//   Instead it's clearer to say:
//       given { Film[] } map { Foo } as { ... }[]
//   This says "for each film, convert to foo, then project to ...".
//   This is clearer.
// Concerns:
//   - This approach works nicely for top-level items,
//     but quickly falls apart for inner collections, where
//     we expect expressions, not query directives.
//     This means we need a different approach for mapping inner collections
//     Something like:
//        given { Film[] } map { Foo } as {
//            thing : map(InnerCollection[], {  targetType: SomeType }) // returns an array.
//        }[]
//     This acheives a similar result, and is supported by the grammar,
//     (although needs an implementation of the map function )
//     but begs the question why we have two implementation appraoches.
K_Map : 'map';


K_Table: 'table';
K_Stream: 'stream';

// Identifier for signalling a mutation within a query
K_Call: 'call';

K_Except : 'except';

K_When: 'when';
K_Else: 'else';

IdentifierToken
    :   Letter LetterOrDigit*
    | '`' ~('`')+ '`'
    ;


StringLiteral
    :   '"' DoubleQuoteStringCharacter* '"'
    |   '\'' SingleQuoteStringCharacter* '\''
    ;


fragment
DoubleQuoteStringCharacter
    :   ~["\\\r\n]
    |   EscapeSequence
    ;

fragment
SingleQuoteStringCharacter
    :   ~['\\\r\n]
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
    :   MINUS? DecimalNumeral /* IntegerTypeSuffix? */
    ;

// Note: Make sure this is defined after IntegerLiteral,
// so that numbers without '.' are parsed as Integers, not
// Decimals.
DecimalLiteral : NUMBER;

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

DOCUMENTATION
   : '[[' .*? ']]';

COMMENT
    :   '/*' .*? '*/' -> channel(HIDDEN)
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> channel(HIDDEN)
    ;

GT : '>' ;
GE : '>=' ;
LT : '<' ;
LE : '<=' ;
EQ : '==' ;
NQ : '!=';

COALESCE : '?:';

LOGICAL_OR : '||';
LOGICAL_AND : '&&';

TRUE  : 'true' ;
FALSE : 'false' ;

MULT  : '*' ;
DIV   : '/' ;
PLUS  : '+' ;
MINUS : '-' ;
POW: '^';

LPAREN : '(' ;
RPAREN : ')' ;

SPREAD_OPERATOR : '...';
