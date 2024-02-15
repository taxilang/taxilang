# queries with given will fail if operations to invoke is ambiguous

- Status: accepted 
- Deciders: Marty Pitt, Serhat Tuncay 
- Tags: [space and/or comma separated list of tags] <!-- optional -->


## Context and Problem Statement

As an extension of removing support for querying on baseTypes from `find {}` queries, the behaviour now throws an exception if the query is ambiguous:

```

service Films {
   operation findFilmsByYear(Year):Film[]
   operation findFilmsByProducer(ProducerId):Film[]
}

given { ProducerId = '123', Year = 2023 }
find { Film[] }
```

## Decision Drivers <!-- optional -->

- See [Find does not query on base types](./20240215-find-does-not-query-on-base-types.md)


