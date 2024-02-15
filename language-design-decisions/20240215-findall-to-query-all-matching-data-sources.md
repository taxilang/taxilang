# findAll to query all matching data sources

- Status: draft 
- Deciders: Marty Pitt, Serhat Tuncay 
- Date: [YYYY-MM-DD when the decision was last updated] <!-- optional. To customize the ordering without relying on Git creation dates and filenames -->
- Tags: [space and/or comma separated list of tags] <!-- optional -->

Technical Story: [description | ticket/issue URL] <!-- optional -->

## Context and Problem Statement

`find { Foo }` used to include subtypes of `Foo` in the query graph.
However, this lead to inconsistent behaviour, as documented in [Find does not query on base types](./20240215-find-does-not-query-on-base-types.md).
So, the behaviour was removed.

Instead, "query for everything" should be an explicit, opt-in behaviour.

For this, we're proposing `findAll {}`


## Outstanding questions

### How are inputs to operations resolved?
eg:

```
model Trade { ... }
model FxTrade inherits Trade { ... }
model EquityTrade inherits Trade { ... }

service Trades {
   operation listFxTrades():FxTrade[]
   operation listFxTrades(TraderId):FxTrade[]

   operation listEquityTrades():EquityTrade[]
}
```

In the above, which services are expected to be invoked in the following query:

```
given { TraderId = '123' }
findAll { Trade[] }
```

Should `listEquityTrades()` be invoked?





## Decision Drivers <!-- optional -->


## Considered Options


## Decision Outcome


### Positive Consequences <!-- optional -->


### Negative Consequences <!-- optional -->


## Links <!-- optional -->

- [Link type](link to adr) <!-- example: Refined by [xxx](yyyymmdd-xxx.md) -->
- … <!-- numbers of links can vary -->
