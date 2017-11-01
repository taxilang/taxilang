# Grammar & Compiler

This project contains the Taxi grammar, and a parser / compiler to 
interpret `taxi` documents to a well-structured object model for tooling.

## Goals
Taxi attempts to be a language for describing services and models.  It is language agnostic,
and intends to serve as the basis for generation.

Taxi is not a programming language, and can't express logic.

 * Concise, but readable.  Favour Brevity, but not terseness.  
 * Ideally, should be readable & authorable by non-technical audiences (BA's)
 * Provide a machine-parseable description of services, and what they do (beyond simple method contracts)
 * Encourage descriptiveness through type systems.
 
### Taxi vs ${otherLanguage}

Swagger, RAML and WSDL's are previous attempts at a similar goal.  However, Taxi is different because:

 * It is a first-class language, rather than using JSON / Yaml / XML to communicate ideas. Other formats get lost in their own markup, and fail to document ideas.

## Language Definition
The [grammar](src/main/antlr4/lang/taxi/Taxi.g4) is the authoritive source of the language, 
and trumps anything documented here which has become stale.

 