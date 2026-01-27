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
    :   K_Import qualifiedName
    ;

namespaceDeclaration
    :   K_Namespace qualifiedName
    ;

namespaceBlock
    :   K_Namespace qualifiedName namespaceBody
    ;


namespaceBody
    : '{' toplevelObject* '}'
    ;

toplevelObject
// Note: typeAlias must come before type, otherwise `type alias string as String` gets parsed as a type, not an alias.
    :   typeAliasDeclaration
    |   typeDeclaration
    |   partialModelDeclaration
    |   enumDeclaration
    |   enumExtensionDeclaration
    |   typeExtensionDeclaration
    |   typeAliasExtensionDeclaration
    |   serviceDeclaration
    |   policyDeclaration
    |   functionDeclaration
    |   annotationTypeDeclaration
    |   query
     // Allowing top-level expressions.
    // Not super-useful for real-world,
    // but make doucmentation and testing easier
    |   expressionGroup
    ;

typeModifier
// A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
    : K_Parameter
    | K_Closed
    ;

typeKind : K_Type | K_Model;

// type aliases
typeAliasDeclaration
    : typeDoc? annotation* K_Type K_Alias identifier aliasedType
    ;

aliasedType
   : K_As typeReference
   ;

typeDeclaration
    :  typeDoc? annotation* typeModifier* typeKind identifier
         typeArguments?
        (K_Inherits listOfInheritedTypes)?
        (typeBody | expressionTypeDeclaration)?
    ;

partialModelDeclaration: typeDoc? annotation* K_Partial typeModifier* K_Model identifier 'from' typeReference;

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

typeExtensionDeclaration
   :  typeDoc? annotation* K_Type 'extension' identifier typeExtensionBody
   ;

// Using by is deprecated, prefer =
expressionTypeDeclaration : ('by'|'=') expressionGroup;

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
expressionInput: (identifier ':')? (nullableTypeReference accessor? | expressionGroup);


// Added for expression types.
// However, I suspect with a few modifications e can simplify
// all expressions to
// this group (fields, queries, etc.).
// This definition is based off of this:
// https://github.com/antlr/grammars-v4/blob/master/arithmetic/arithmetic.g4
// which ensures order-of-precedence and supports grouped / parenthesis
// expressions
expressionGroup:
   expressionGroup (MULT | DIV | MOD) expressionGroup
   | negated expressionGroup
   | expressionGroup (PLUS | MINUS) expressionGroup
   // The below is added for lambdas, but not sure order of precedence
   // is correct. TBD.
   | expressionGroup memberReference // Member reference must have higher precedence that cast
   | expressionGroup '.' methodCall // must be above cast
   | expressionGroup '.' identifier // must be above cast
   | castExpression expressionGroup
   | LPAREN expressionGroup RPAREN
   | (PLUS | MINUS)* expressionAtom
   | expressionGroup comp_operator expressionGroup
   | expressionGroup COALESCE expressionGroup
   | expressionGroup LOGICAL_AND expressionGroup
   | expressionGroup LOGICAL_OR expressionGroup
   | whenBlock
   // Inputs go last, so that when parsing lambdas, the inputs are the LHS and everything remainin goes RHS.
   // Might not work for nested lambdas, if that's a thing.
   | expressionInputs expressionGroup
   | expressionGroup typeProjection
   | valueArray;

// readFunction before typeType to avoid functons being identified
// as types
// 1-Oct: Tried collapsing scalarAccessorExpression into this, but it caused errors.
// Would like to simplify...
// 31-Jul-24: Removed anonymousTypeDefinition from expressionAtom,
// as it made it impossible to use cast syntax for constructor-like behaviour
// eg:  throw((NotAuthorizedException) { message: 'Not Authorized' })
// should be a cast expression with a value.
// No compiler tests broke, lets see what happens in Orbital
 // TODO :This has literla and literalArray, but not value, which also includes objects.
 // Should we replace literal | literalArray with value?
expressionAtom: functionCall
               | typeExpression
               // Must come after TypeExpression, or
               // parsing of function calls fails
               | operationInvocation
               | typeProjection
               | fieldReferenceSelector
               | objectValue
               | valueArray
               | literal;

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
   : typeDoc? annotation* 'annotation' identifier (K_Inherits listOfInheritedTypes)? annotationTypeBody?;

negated: NOT;
annotationTypeBody: '{' typeMemberDeclaration* '}';

castExpression: LPAREN typeReference RPAREN;

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
//
// TODO : 24-Mar-25: All other things like this have moved into ExpressionGroup.
// This needs to as well...
fieldReferenceSelector: propertyFieldNameQualifier qualifiedName;

typeReferenceSelector: typeReference;

whenCaseDeclaration:
   caseDeclarationMatchExpression '->' (  /*caseFieldAssignmentBlock |  */  expressionGroup | scalarAccessorExpression);

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
  :   fieldModifier? identifier (':' (anonymousTypeDefinition | fieldTypeDeclaration | expressionGroup ))? typeProjection?
  ;


// Used to describe navigation from one entity to another
// Eg from Type to Property Type (Person::FirstName)
// Or from Service to Operation (PersonService::findAllPeople)
// Note: Array marker here is unfortunate, as in a service context
// it doesn't make sense - but we'll have to enforce that at the compiler., not the grammar
// See also expressionGroup, where this concept is also represented
memberReference: '::' typeReference arrayMarker?;
// MP: 25-Mar-25: We used to support a more complex syntax here, in the form of
// `(Foo::Bar)[]` which differentiates from Foo:Bar[]
// However, after changing the grammar to support expressions on the LHS of
// member references, this because complex to support, and nobody is using it.
// So, have dropped reference to this for now




// fieldType usages allow richer syntax with additional features like
// inline type definitions, optionality, aliases and accessors.
// Other type usage sites are not as flexible (eg., return type of an operation)
fieldTypeDeclaration: typeExpression? inlineInheritedType? accessor?;

// A type expression is both a type, with optional contraints.
// eg:
// Film( ActorId == 123 )
typeExpression
   : (nullableTypeReference parameterConstraint? // constraints are not permitted against anonymous types
   | inlineAnonynousType);

// This is a short-term workaround.
// Problem:
// Statements like the following should be possible:
// stream { Foo } as {
//   thing: Thing[] = listOf({
//        someConst: RefCode = "IAmAConstantValue"
//        someExpression: FullName = concat(FirstName, LastName)
//   })
// }[]
//
// Currently, this works if we extract the inline type def out to a type
// eg: listOf(MyExtractedType)
// However, that moves the projection logic onto the type, making the type single-shot.
// In a scenarion where we're trying to define projection logic to map to something like a DB record,
// we can't stil projection logic on the model iteslf, as it tightly couples the model to a single projection scenario.
// It also makes reading the type back from the db very difficult.
//
// However, our current grammar differentaites between object literals and type expressions.
// I think that as the type expression syntax has gotten richer, this distinction may not make sense anymore.
// There's a seperate branch for collapsing those two things together, but it's a signficiant change.
// So this statement allows us to define an anonymous type inline.
// eg: the above becomes:
// stream { Foo } as {
//   thing: Thing[] = listOf(type {
//        someConst: RefCode = "IAmAConstantValue"
//        someExpression: FullName = concat(FirstName, LastName)
//   })
// }[]
// This is undesirable long-term, as we're adding more confusing and inconsistent syntax, which
// ultimately isn't needed.
// (While it's early days on the refactor branch, I haven't yet found a scenario which can't be addressed
// by collapsing type definitions and object expressions together.
inlineAnonynousType : K_Type anonymousTypeDefinition;

typeReference
    :   qualifiedName typeArguments? arrayMarker?;
    //
unionType : typeReference ('|' typeReference)*;
intersectionType : typeReference ('&' typeReference)*;

// Use in call sites where optional types are permitted
nullableTypeReference : (typeReference | unionType | intersectionType) Nullable?;

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
    :    typeDoc? annotation* lenientKeyword? 'enum' identifier typeArguments?
         ((K_Inherits enumInheritedType) | ('{' enumConstants? '}'))
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
   : '(' (literal | objectValue) ')'
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



inlineInheritedType
   : K_Inherits typeReference
   ;

typeAliasExtensionDeclaration
   : typeDoc? annotation* K_Type 'alias extension' identifier
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
    : literal
    | objectValue
    | qualifiedName // Support enum references within annotations
    | annotation
    | valueArray
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

queryFilterCapability: K_Filter( '(' filterCapability (',' filterCapability)* ')');

filterCapability: EQ | NQ | IN | NOT_IN | LIKE | GT | GE | LT | LE;

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

operationScope : K_Read | K_Write;

operationReturnType
    : ':' typeReference ('(' (operationReturnValueOriginExpression ',')? ((expressionGroup? (LOGICAL_AND SPREAD_OPERATOR)?) | SPREAD_OPERATOR?) ')')?
    ;


 // typeReference
     //    :   qualifiedName typeArguments? arrayMarker? optionalType? parameterConstraint? (aliasedType? | inlineInheritedType?)?

operationParameterList
    :   operationParameter (',' operationParameter)*
    ;


operationParameter
// Note that only one operationParameterConstraint can exist per parameter, but it can contain
// multiple expressions
     :   typeDoc? annotation* (parameterName)? ((nullableTypeReference ( '(' expressionGroup ')')?  varargMarker? parameterDefaultValue?) | lambdaSignature)
     ;

parameterDefaultValue: '=' expressionGroup;

varargMarker: '...';
// Parameter names are optional.
// But, they must be used to be referenced in return contracts
parameterName
    :   identifier ':'
    ;

parameterConstraint
    :   '('expressionGroup? ')'
    ;


// The return value will have a relationship to a property
// received in an input (incl. nested properties)
operationReturnValueOriginExpression
    :  'from' qualifiedName
    ;


propertyFieldNameQualifier : 'this' '.';

comp_operator : GT
              | GE
              | LT
              | LE
              | EQ
              | NQ
              | IN
              | NOT_IN
              ;


policyDeclaration
    :  typeDoc? annotation* 'policy' identifier 'against' typeReference expressionInputs? '{' policyRuleSet* '}';


policyRuleSet : operationScope? policyScope? '{' expressionGroup '}';

policyScope : 'internal' | 'external';


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
functionDeclaration: typeDoc? K_Declare? (functionModifiers)* 'function' typeArguments? identifier '(' operationParameterList? ')' ':' nullableTypeReference functionExpression?;

// 26-Sep-24: Adding function expressions.
// This is because expression types have started to hit bounds of complexity - especially
// around returning array values.
// The intent of functionExpression is to provide composibility of other functions.
// Ultimately, logic of complex functions would still be pushed down into an implementation language.
// But, functionExpressions would allow us to compose together other functions into something useful.
// Also, unlike expression types (which now cannot return arrays), functions can return arrays
functionExpression: '->' expressionGroup;

functionModifiers: K_Query | K_Extension;


// Could be MyType( foo == bar ), or myFunction( param1, param1 )
// Note: This is a top-level function call, not a method (or extension function) invoked on
// a call target
functionCall: qualifiedName '(' argumentList? ')';

// This is a call on something - almost always an extension function.
// The main difference here is that we don't accept a qualified name (ie.,
// many dots), only a single identifier.
// This is important for disticntion between method calls, and property access
// (eg., a.b.c().d.e)
methodCall: identifier '(' argumentList? ')';

// A list of arguments passed into a function call
// Permits trailing commas.  foo(a, )
// This seems to clarify the grammar, such that everything inside the parenthesis
// is an argument. Otherwise, we were finding the grammar parsed trailing commas
// as an addiitonal type member declaration.
argumentList
    : argument  (',' argument?)* // allowing trailing commas helps clarify the grammar
    ;

argument: literal |  scalarAccessorExpression | fieldReferenceSelector | typeReferenceSelector | memberReference | expressionGroup;

columnIndex : IntegerLiteral | StringLiteral;

expression
    :   '(' expression ')'
    |   literal
    |   identifier;

qualifiedName
    :   identifier ('.' identifier)*
    ;

arrayMarker
   : '[' ']'
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

queryName: K_Query identifier queryParameters?;

queryParameters: '(' queryParamList ')';

queryParamList: queryParam (',' queryParam)*;

queryParam: annotation* identifier ':' nullableTypeReference;

queryDirective: K_Stream | K_Find | K_Map;
findDirective: K_Find;


givenBlock : 'given' '{' factList '}';

factList : fact (',' fact)*;


// MP: 04-Sep-23: Passing the value used to be optional.
// What was the use-case for this?
// Improving strictness around given blocks that refer to variables from queries,
// by clarifying the syntax. (factDeclaration vs variableName)
// TODO: (5-Jan-24): There are several ideas with high overlap that need clarifying: fact, argument, operationParameter.
// I suspect facts are invalid.
factDeclaration : (variableName ':')? typeReference '=' expressionGroup;
fact: factDeclaration | variableName;

//value : objectValue | valueArray | literal | expressionGroup;

objectValue: '{' objectField? (',' objectField)* '}';
objectField : (identifier | StringLiteral) ':' expressionGroup;
valueArray: '[' expressionGroup? (',' expressionGroup)* ']';

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
   (queryDirective ( ('{' queryInputExpressionGroup '}') | anonymousTypeDefinition  ) expressionGroup? /* this expression now provides the projection */ mutation? serviceRestrictions?) |
   mutation;

// Just a wrapper to disambiguate multiple expressionGroups
// in the queryOrMutation type.
queryInputExpressionGroup: expressionGroup;

serviceOrMemberReference: typeReference memberReference?;
serviceOrMemberReferenceList: serviceOrMemberReference (',' serviceOrMemberReference)*;

// Allows controlling which services are included / excluded from
// a query
// eg:
// find { Foo[] } as {
//   .. snip ..
// } using {
//   SomeService,
//   AnotherService::SpecificOperation
// }
// Note that Using and Excluding are mutually
// exclusive.
// By definition, if things are defined with Using
// then everything else is excluded.
// Likewise, an Exclusion list includes everything else.
serviceRestrictions: (K_Using | K_Excluding) '{' serviceOrMemberReferenceList '}';


// Note: 23-Apr-24...
// tried allowing both 'as' and '->' here, but it created ambiguity with the
// expressionInputs block, causing failing tests.
typeProjection: (K_As) expressionInputs? (anonymousTypeDefinition | typeReference);

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

mutation: K_Call ((typeReference memberReference) | operationInvocation) typeProjection?;

operationInvocation: typeReference memberReference LPAREN argumentList? RPAREN;

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
// Unfortunately, we can't include K_Call here, as it
// breaks the grammar detecting mutations

// MP: 25-Mar-25: Unfortunately, allowing 'K_Extension' in the identifier group breaks the grammar parsing of
// type extensions, so people are just gonna have to use backticks for that one.
identifier:
   K_Table | K_Stream |  K_Find | K_Map |  K_Except |  K_Filter | K_Query |  K_Read | K_Write | K_Declare | K_Type | K_Model | K_Alias | K_Partial | IdentifierToken
   // | K_Call        // These are excluded as they break parsing elsewhere in the grammar
   // | K_Extension   // These are excluded as they break parsing elsewhere in the grammar
   // | K_As          // These are excluded as they break parsing elsewhere in the grammar
   // | K_Given       // These are excluded as they break parsing elsewhere in the grammar
   ;

K_Given: 'given';
K_Find: 'find';
K_As: 'as';
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

K_Type : 'type';
K_Model : 'model';
K_Alias: 'alias';
K_Table: 'table';
K_Stream: 'stream';
K_Filter: 'filter';
// Identifier for signalling a mutation within a query
K_Call: 'call';

// Operation scopes
K_Read : 'read';
K_Write : 'write';

K_Except : 'except';

K_When: 'when';
K_Else: 'else';

K_Query: 'query';
K_Extension: 'extension';

K_Declare: 'declare';

K_Using: 'using';
K_Excluding: 'excluding';

K_Import: 'import';
K_Namespace: 'namespace';
K_Parameter: 'parameter';
K_Closed: 'closed';
K_Inherits: 'inherits';

K_Partial: 'partial';

IdentifierToken
    :   Letter LetterOrDigit*
    | '`' ~('`')+ '`'
    ;


StringLiteral
    :   '"""' MultiLineStringCharacter*? '"""'
    |   '"' DoubleQuoteStringCharacter* '"'
    |   '\'' SingleQuoteStringCharacter* '\''

    ;


// Add this new fragment for multi-line string content
fragment
MultiLineStringCharacter
    :   ~["]           // Any character except quote
    |   '"' ~["]       // Single quote followed by non-quote
    |   '""' ~["]      // Double quotes followed by non-quote
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

WS  :  [ \t\r\n\u000C]+ -> channel(HIDDEN)
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

NOT : '!';
MULT  : '*' ;
DIV   : '/' ;
PLUS  : '+' ;
MINUS : '-' ;
MOD: '%';

LPAREN : '(' ;
RPAREN : ')' ;

SPREAD_OPERATOR : '...';
