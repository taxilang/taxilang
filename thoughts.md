# Taxi

Taxi is a language for defining resources and contracts between systems.
Similar to Swagger / BAML, and protobuf specs.  (Note - not a serialization format.)

## Design goals:

### Language agnostic
Taxi will (initally) compile to BAML, which can then be output
to many different languages.

Eventually, the BAML step should be removed.

### Concise & expressive
Steal ideas from Kotlin & GraphQL for expressiveness

### Extensible
API's are composable -- publishers define the contract, but consumers
inject metadata around how they are used.

Eg: Annotations for defining persistence, mappings, etc.
These are consumer extensions to the publishers contract.

## Non goals
 - Serialization, and therefore backwards compatibility.
