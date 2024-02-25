# Add expressions to scope declarations

- Status: [draft | proposed | rejected | accepted | deprecated | … | superseded by [xxx](yyyymmdd-xxx.md)] <!-- optional -->
- Deciders: Marty Pitt
- Date: [YYYY-MM-DD when the decision was last updated] <!-- optional. To customize the ordering without relying on Git creation dates and filenames -->
- Tags: [space and/or comma separated list of tags] <!-- optional -->

Technical Story: [description | ticket/issue URL] <!-- optional -->

## Context and Problem Statement

Sometimes when you're defining a projection, you need access to a variable that you don't want to include
in the output.

Previously, scope declarations allowed refining scope to data elements as the input into the projection.
However, we didn't allow expression evaluation, meaning you were limited to exactly the data defined on the source.

This change allows expressions to be used in scope declarations, which allow for richer inputs into projections.

For example:

```
find { Film(FilmId == 1) } as (first(Actor[])) -> {
   starring : ActorName
}
```

In this example, we want to operate only on the first actor discovered from Film, effectively flattening the array.



## Links <!-- optional -->

- [Link type](link to adr) <!-- example: Refined by [xxx](yyyymmdd-xxx.md) -->
- … <!-- numbers of links can vary -->
