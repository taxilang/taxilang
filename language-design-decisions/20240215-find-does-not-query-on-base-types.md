# `find` queries do not query on base types

- Status: accepted 
- Deciders: [list everyone involved in the decision] <!-- optional -->
- Date: [YYYY-MM-DD when the decision was last updated] <!-- optional. To customize the ordering without relying on Git creation dates and filenames -->
- Tags: [space and/or comma separated list of tags] <!-- optional -->

## Context and Problem Statement

Historically, `find { .. }` used to invoke all zero-arg operations that match by base type.

This was useful for scenarios like:

```
model Trade
model FxTrade inherits Trade {}
model EquitiesTrade inherits Trade {}

service Trades {
   operation findFxTrades():FxTrade[]
   operation findEquitiesTrades():EquitiesTrade[]
}

// query:
find { Trade[] }

// should call both findFxTrades and findEquitiesTrades
```

### Problems
However, while this is desirable, there are issues with it's implementation:

#### Operations with params not invoked
This is only implemented in `DirectQueryInvocationStrategy` - ie., zero-arg operations, so anything that accepts inputs are excluded.  This is not the "reasonably expected" behaviour.

## Decision Drivers <!-- optional -->

This was brought to a head when addressing issues in the querying implementation, when using `given { ... } find { ... }`:

```
service Films {
   operation findFilms():Film[]
   operation findFilmsByProducer(ProducerId):Film[]
}

given { ProducerId = "123" }
find { Film[] }
```

The reasonable expected behaviour above is that `findFilmsByProducer` is invoked. However, the current implementation invokes `findFilms`.
This was down to a bug in the `DirectQueryInvocationStrategy`, which didn't consider provided facts.

After fixing that bug, it left another outstanding issue, in that ALL the available candidate methods get called.
This is clearly undesierable, and violates "The prinicipal of least surprise".

However, it's a legacy decision, related to supporting the feature being discussed here.

After consideration, the decision was made to call the most specific operation - see also [this ADR](./20240215-queries-with-given-will-fail-if-operations-to-invoke-is-ambiguous.md)


## Links <!-- optional -->

- [Link type](link to adr) <!-- example: Refined by [xxx](yyyymmdd-xxx.md) -->
- … <!-- numbers of links can vary -->
