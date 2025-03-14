Brain dump of notes for building / developing the typescript code-gen toolchain

## Native image / Codegen CLI

Our npm tooling calls a native image for the compiler.
These are built on Gitlab by creating the `taxiql-code/taxiql-codegen-cli` project.

These are then copied to `typescript/taxiql-codegen/packages/{os}/` and npm published.

Each of these is then an optional dependency on the `@orbitalhq/taxiql-codegen` package, which is OS-specific.

Npm picks the correct one for the users OS when doing an `npm install`.

## Versioning

### TaxiQL Codegen tooling
The codegen tooling is versioned / evoles with the rest of the Taxi package - meaning there's frequent
small releases to codegen. This is a concious choice, so the entire Taxi toolchain of a specific version is
compatible.


### TaxiQL Client
The TaxiQL client evolves more slowly, and is versioned independent from the rest of the toolchain.
This is because we expect the TaxiQL typescript client to evolve at a different (slower) pace from the underlying langauge,
and frequent releases (which do nothing) will be annoying.

## Building / testing
To build / test the TaxiQL client:

 * 
