# enums may now have objects for values

- Status: accepted
- Deciders: Marty Pitt
- Date: 2024-10-08
- Tags: 

Technical Story: https://projects.notional.uk/youtrack/issue/ORB-670/Ability-to-define-an-object-value-in-an-enum-and-use-it-for-map-type-lookups

## Context and Problem Statement

Users often need to map between a set of static values.
They can make a service call to resolve these values, but that means pushing essentially config into microservices,
which can be resolved locally.

Some users would rather have all their mapping logic encapsulated within taxi / orbital, without having to
make additional service calls.

## Considered Options

We considered either:

 * Supporting Enums with object values
 * Introducing `const` declarations containing static values (which would be maps)

Looked at different languages to get inspiration - notably:
 * Typescript **does not** support enums with object values, primarily because of challenges with type inference
 * Kotlin **does** support enums with object values

Adding `const` is likely to be more flexible, but is a larger change to the runtime, including scoping rules.

Therefore, decided to use enums

## Decision Outcome

Added enums with object support.

Note that if the value is an object, it must be typed, using the new `Enum<T>` declaration

```taxi
model ErrorDetails {
   code : ErrorCode inherits Int
   message : ErrorMessage inherits String
}
enum Errors<ErrorDetails> {
   default BadRequest({ code : 400, message : 'Bad Request' }),
   Unauthorized({ code : 401, message : 'Unauthorized' })
}
```


