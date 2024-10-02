# Allow functions to provide implementations

- Status: [draft | proposed | rejected | accepted | deprecated | … | superseded by [xxx](yyyymmdd-xxx.md)] <!-- optional -->
- Deciders: Marty Pitt
- Date: 2024-09-26

## Technical Story: 

To prevent complexity in the type system, we decided to disallow expression types returning collection types (`Stream<>`, `Map<>` or `Array<>`).

However, that means certain ideas can't be expressed in Taxi.

Meanwhile, since `declare function` was introduced, the number of stdlib functions has grown, such that complex logic is now
expressable simply by composing std lib functions together.

Also, we already support defining expressions - which are basically functions anyway.

### Keeping queries DRY
Currently, people can express logic in a query using expressions (such as `when`, or expressions on fields).
However, they can't extract that logic anywhere, and end up having to repeat the implementation.


## Decision Outcome

Modify the grammar so that functions can be declared.

```taxi
function filterToAdults(person:Person) -> person.filter( (Age) -> Age > 18 ) 
```

 * If a function has an implementation, it's no-longer a `declare function` - this is consistent with Typescript's 
   approach here, where `declare` indicates "This function has an implementation elsewhere"
 * Return types are implied. We might revisit this later, but this reduces the amount of changes to the grammar.
 * Implementation is still restricted to what is possible using other stdlib functions, or the grammar itself.
 * We don't have variables or constants, so everything must be chained.
   * If we revisit this, we can consider a brace syntax.



## Links

- [ADR: Expression types may not return collections](20240926-expression-types-may-not-return-collections.md)
- [Issue](https://projects.notional.uk/youtrack/issue/ORB-655/Allow-function-implementations)
