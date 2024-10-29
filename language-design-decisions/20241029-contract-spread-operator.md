# ADR: Contract Spread Operator for Operation Return Types

## Status
Accepted

## Context
A bug was discovered where services without explicit contracts were being invoked incorrectly during projections. 

When this was fixed, it revealed that many tests relied on this incorrect behavior, highlighting a broader issue: 
the verbosity of writing explicit contracts was leading developers to omit them entirely.

(See: [ORB-764](https://projects.notional.uk/youtrack/issue/ORB-764/Constraint-on-projection-not-applied-if-the-projected-data-is-of-same-type))

The core issues were:
1. Services without contracts could be invoked with incorrect parameter mappings
2. Writing explicit contracts for simple equality matching was overly verbose
3. Developers were omitting contracts due to poor ergonomics
4. Existing tests relied on incorrect behavior, indicating widespread impact

## Decision
We will introduce a spread operator (`...`) for operation contracts that provides a concise way to specify parameter matching:

```taxi
service Films {
   // Simple usage - applies equality matching for all parameters
   operation findFilm(filmId:FilmId):Film(...)

   // Mixed usage - combines explicit constraints with spread
   operation findFilms(yearReleased:YearReleased, rating:Rating):Film[](
      YearReleased > yearReleased && ...
   )
}
```

### Rules
1. The spread operator implies equality matching for all parameters not explicitly referenced
2. Only one spread operator is allowed per contract
3. The spread operator can be combined with explicit constraints
4. The spread operator must come after the constraint expression

## Alternatives Considered

### 1. Bang (!) Operator
```taxi
operation findFilm(FilmId):Film!
```
Rejected because:
- Has different meanings in other languages (null assertion, negation)
- Less clear about its intent

### 2. Equality Operator
```taxi
operation findFilm(FilmId):Film==
```
Rejected because:
- Unfamiliar syntax pattern
- No precedent in other languages
- Could be confused with comparison operators

### 3. Functional Language Patterns
Considered patterns from F# and Haskell but found they were solving different problems and didn't fit our use case as well.

## Consequences

### Positive
- More concise and maintainable contract specifications
- Prevents accidental misuse of services without contracts
- Maintains flexibility for complex cases via mixed usage
- Familiar syntax pattern (spread operator)
- Reduces boilerplate while preserving semantic clarity

### Negative
- New syntax for developers to learn
- Might not be immediately obvious what parameters are being matched
- Could be misused in cases where explicit contracts would be clearer

### Neutral
- Existing code with explicit contracts remains valid
- Migration can be gradual

## Implementation Notes
1. Update the parser to support the spread operator in contract expressions
2. Add validation for spread operator usage (single instance per contract)
3. Implement contract expansion during compilation/interpretation
4. Update documentation and examples
5. Create migration guide for existing services without contracts
