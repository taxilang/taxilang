# Expression types may not return collections

- Status: accepted
- Deciders: Marty Pitt, Serhat Tuncay
- Date: 2024-09-26

Technical Story: [description | ticket/issue URL] <!-- optional -->

## Context and Problem Statement

Expression Types are good and helpful, but they introduce significant complexity into the type system
if they return Arrays.

In general, Expression Types return a new subtype of the result of the expression:

```taxi
// UppercaseName subtypes String
type UppercaseName by (name:Name) -> name.toUppercase()
```

This doesn't scale well with arrays:

```taxi
type Adults = (Person[]) -> Person[].filter( (Age) -> Age > 18 )
```

What is the type of `Adults` here?

## Considered Options

### Using compiler magic to build a type alias:
```angular2html
type Adults = (Person[]) -> Person[].filter( (Age) -> Age > 18 )
// becomes...
type alias Adults = Person[] 
```
This just seems wrong.

### Subtyping Array:
```taxi
type Adults = (Person[]) -> Person[].filter( (Age) -> Age > 18 )
// beccomes...
type Adults inherits Array<Person> 
```

In general, we've tried to avoid subtyping generic structural types, as adding semantics
on the structure is not informative or helpful.

ie., `Array<Adult>` (where `Adult inherits Person`) is clarifying - the semantics of "Adultness" have been
pushed into the member type.

Also, existing tech debt in utility classes makes this unappealing - as we do things like `Arrays.isArray(typeName)`

## Decision Outcome

Don't allow expression types to return `Array<>` or `Stream<>` or `Map<>`, and encourage people to use functions instead.

In addition, to support use cases that are not allowed via expression types, we are introducing
function implementations.

## Future work:
In the future, we may introduce an additional restriction, which clarifies behaviour around returning non-collection
generic types. 

However, generics aren't really used in Taxi outside of collection types, which are already restricted.
Therefore, we don't know enough about the usage to decide how to implement this.



## Links

- [Issue](https://projects.notional.uk/youtrack/issue/ORB-654/Expression-types-may-not-return-arrays)
- [ADR: Add functions](20240926-allow-functions-to-provide-implementations.md)
