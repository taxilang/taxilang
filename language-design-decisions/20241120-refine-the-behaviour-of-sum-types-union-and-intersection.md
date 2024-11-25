# ADR: Sum Types in Taxi

- Status: Accepted
- Date: 2024-11-20

## Context and Problem Statement

1. Union types (`A | B`) exist in Taxi but lack clear behavior definition
2. Users commonly request stream joining functionality where events emit only after both streams have data
3. Field name collisions in union types currently result in dropped fields
4. Need to establish consistent behavior for property merging in sum types

## Decision Drivers

- Stream joining is a primary use case
- Field name collision handling needs clear rules
- Developer familiarity with TypeScript and other languages
- Query behavior needs to be predictable and useful

## Considered Options

1. Follow TypeScript's sum type behavior exactly
2. Create new sum type behavior specific to Taxi's needs
3. Remove sum types entirely

## Decision Outcome

Chosen option: "Create new sum type behavior", because it best serves Taxi's stream joining use case while 
maintaining familiar syntax.

### Implementation Details

Two types of sum types:
- Union Type (`A | B`): Contains all properties from both types
- Intersection Type (`A & B`): Contains all properties from both types

Field name resolution:
```
1. Unique fields: Keep original name
2. Same name + same type: Single field
3. Same name + different type: Prepend model name
4. Same model names: Prepend fully qualified name
```

Query behavior:
```taxi
stream { A | B }  // Emits on either A or B
stream { A & B }  // Emits only after both A and B
find { A | B }    // Returns if either exists
find { A & B }    // Returns if both exist
```

### Positive Consequences

- Clear rules for field name collisions
- Intuitive stream joining behavior
- Familiar syntax from TypeScript
- Consistent behavior across query types

### Negative Consequences

- Diverges from TypeScript's property behavior
- May require additional documentation
- Potential initial confusion for TypeScript developers


## Implementation considerations
A sum type can be declared as a top-level type:

```
model TweetAndAnalytics = Tweet | Analytics
```

Although `UnionType` and `IntersectionType` are first
class entities within the compiler, this is compiled
as a `ObjectType`, with a `TypeExpression` returning a `UnionType`

50/50 if this is the right call: 
 * On first look, you'd think this declares a UnionType
 * However, the sytax IS a type expression, so returning a type expression remains consistent
 * By the time the compiler processes the type expression, we've already parsed the type declaration as an Object type (and potentially other types already reference it). Changing this to a UnionType is very complex.

### Arrays, Streams and Maps
Members of Sum types must all be the same structural type.

Where the members of the sum type are a structural type (Array / Stream / Map),
the the union type becomes a structural type too.

eg:

```taxi
// valid - Union type is object type
stream { A | B } 
// invalid
stream { A[] | B }
// valid - Union type is array type Array<A|B>
stream { A[] | B[] }

// invalid
type FooType = A[] | B 
type FooType = A[] | Stream<B>

// valid - Union type is Stream<A|B>
type FooType = Stream<A> | Stream<B>
```

## Problems
There's an unresolved conflict with an earlier ADR - [20240926-expression-types-may-not-return-collections.md](20240926-expression-types-may-not-return-collections.md).

Based on the new direction, this should be valid:

```taxi
type Foo = A[] | B[]
type Bar = A[] & B[]
```

However, this makes the type a collection, which we disallowed.
We'll need to decide how to move forward on this.

Notably, as a workaround, this IS valid:

```taxi
find { A[] | B[] }
```

## Links

- Related to bug: Field dropping in union types


## Raw notes

* We've support union types `A | B` in the spec for ome time, but the bheaviour wasnt clearly defined
* Their main purpose is in stream joining : `stream { A | B }`
* People who used this feature almost always asked "Can you modify this so it only emits after both streams have emitted?"

### Terminology
We're referring to these as `sum types` - taken from Haskell.
However, that's only used interanlly within the code base.

We'll have two subtypes of sum types - `UnionType` (existing) and `IntersectionType` (new).
The difference between the two only occurs when querying.

This differs from Typescript (who we generally look to for language design inspiration). However, we take the view that Typescript's naming for this is confusing.

| Sum type kind     | Syntax   | Typescript Behaviour                                 | Taxi Behaviour                          |
|-------------------|----------|------------------------------------------------------|-----------------------------------------|
| Union Type        | `A \| B` | Only contains the properties present on both A and B | Contains all properties from both types | 
| Intersection Type | `A & B`  | Contains all properties from both types              | Contains all properties from both types | 


### Attributes
A bug exists that in the current implementation, if creating a union type of `A | B` and both A and B have a field with the same
name, then one of the fields is dropped.  The discovery of why this bug occurrs has triggered the entire rethink of sum types.

In both Union Types and Intersection Types the same rules will apply for merging fields:
* If the field name is unique across both types, then the field name is used unchanged
* If the field name is present on both types, with the same type defintiion, then the field appears once (not twice), with the field name unchanged
* If the field name exists on both types, with a different type, then both types have the owning model's type name prepended (without the namespace)
* If both models have the same name (so prepending the model name does not make the field name unique), then the fully qualified name is prepended to the field name.

### Behaviour in queries
```taxi
// emits when either A or B emits
stream { A | B }

// Emits only after A AND B have emitted
stream { A & B }

// Returns if either A or B (or both) can be fetched:
find { A | B }

// Only returns if BOTH A and B can be fetched:
find { A & B }
```

