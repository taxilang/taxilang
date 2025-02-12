# ADR: Partial Models in Taxi

- Status: Partially Implemented
- Date: 2024-02-12

## Context and Problem Statement

We need a way to represent models where all fields are optional/nullable, particularly for:
1. PATCH-style REST API operations
2. Partial data scenarios in queries
3. Optional field updates

Currently, developers must manually create new models with nullable fields, leading to type proliferation.

## Implementation status
This feature is partially implemented:
 * `partial model` is defined, copying fields (making them nullable)
 * all referenced types also get a partial implementation

### Not yet implemented:
 * inline partial types on operation signtaures (Deferred, but will likely implement)
 * `find as partial` - not implemented until it's clear what use-case this would solve

## Decision Drivers

- Need to avoid type proliferation
- Must maintain type safety
- Should feel natural in Taxi's grammar
- Must work with existing type system
- Common REST API patterns should be easy to express
- Must work consistently with arrays and TaxiQL

## Considered Options

1. TypeScript-style `Partial<T>` with compiler support
```taxi
model PartialPerson inherits Partial<Person>
```

2. Keyword modifier approach with `partial model`
```taxi
partial model PartialPerson inherits Person
```

3. Type transformation with `from` keyword
```taxi
partial model PartialPerson from Person
```

4. Type modifier on references
```taxi
operation update(person: partial Person)
```

## Decision Outcome

Chosen combination of options 3 and 4:

1. Explicit model declarations:
```taxi
partial model PartialPerson from Person
```

2. Inline type references:
```taxi
operation update(person: partial Person)
```

### Rules
1. Partial models cannot have bodies
2. `partial` keyword only applies to models, not types
3. Type transformation rules:
   - All fields become nullable (append `?`)
   - Model references are transformed to their partial versions
   - Arrays of models become nullable arrays of partial models
   - Nested objects have all their fields made nullable

Example transformations:
```taxi
model Person {
    name: Name
    friends: Person[]
    address: {
        street: Street
        city: City
    }
}

// Expands to:
model PartialPerson {
    name: Name?
    friends: PartialPerson[]?  // Note transformation of element type
    address: {
        street: Street?
        city: City?
    }?
}
```

The compiler will automatically create partial versions of referenced models as needed. This includes handling:
- Cyclic references
- Deep nesting
- Self-referential structures

### Positive Consequences

- Clear syntax for both declaration and usage
- No breaking changes to type system
- Natural extension to grammar
- Supports array type transformations
- Works with TaxiQL semantic matching

### Negative Consequences

- Diverges from TypeScript's approach
- Requires compiler to handle type transformation
- Generated type names for inline usage could be surprising

## Implementation Notes

1. Grammar changes:
- Add `partial model` declaration with mandatory `from` keyword
- No body allowed in partial model declaration
- Add `partial` as type modifier

2. Compiler behavior:
- Recursively transform model references to their partial versions
- Track model transformations to prevent infinite recursion with cyclic references
- Generate unique types for inline usage
- Create dependent partial models automatically as needed
- Ensure TaxiQL engine understands that `T` can populate `partial T`

3. Error messages:
- Clear errors for invalid partial model bodies
- Clear errors for invalid `partial type` usage

## Alternatives Considered

We considered but rejected:
- TypeScript-style `Partial<T>` as it required substantial grammar changes
- `inherits` keyword as it implied incorrect type relationships
- Parameter modifiers instead of type modifiers

## Open Questions

- How should we handle naming conflicts for compiler-generated partial types?
- Should we add warnings when the same partial type is used multiple times without explicit declaration?


# Test Cases for Partial Models

## Type Transformation Tests

1. Basic field transformations:
```taxi
model Person {
    id: PersonId
    name: Name
    age: Age
}

partial model PartialPerson from Person
// Should expand to:
// model PartialPerson {
//     id: PersonId?
//     name: Name?
//     age: Age?
// }
```

2. Model reference transformations:
```taxi
model Employee {
    id: EmployeeId
    supervisor: Employee
    team: Employee[]
}

partial model PartialEmployee from Employee
// Should expand to:
// model PartialEmployee {
//     id: EmployeeId?
//     supervisor: PartialEmployee?
//     team: PartialEmployee[]?
// }
```

3. Nested objects:
```taxi
model Address {
    street: Street
    city: City
}

model Contact {
    name: Name
    address: Address
    alternateAddresses: Address[]
}

partial model PartialContact from Contact
// Should expand to:
// model PartialContact {
//     name: Name?
//     address: PartialAddress?
//     alternateAddresses: PartialAddress[]?
// }
```

4. Deep nesting:
```taxi
model Company {
    name: CompanyName
    ceo: Employee
    departments: Department[]
}

model Department {
    name: DepartmentName
    head: Employee
    staff: Employee[]
}

partial model PartialCompany from Company
// Should expand to:
// model PartialCompany {
//     name: CompanyName?
//     ceo: PartialEmployee?
//     departments: PartialDepartment[]?
// }
```

5. Self-referential structures:
```taxi
model Node {
    id: NodeId
    parent: Node
    children: Node[]
}

partial model PartialNode from Node
// Should expand to:
// model PartialNode {
//     id: NodeId?
//     parent: PartialNode?
//     children: PartialNode[]?
// }
```

6. Cyclic references:
```taxi
model Author {
    name: Name
    books: Book[]
}

model Book {
    title: Title
    author: Author
}

partial model PartialAuthor from Author
// Should expand to:
// model PartialAuthor {
//     name: Name?
//     books: PartialBook[]?
// }

// Compiler should automatically create:
// model PartialBook {
//     title: Title?
//     author: PartialAuthor?
// }
```

7. Complex nested structures:
```taxi
model Organization {
    details: {
        name: Name
        address: {
            street: Street
            city: City
        }
    }
    branches: {
        location: Address
        manager: Employee
    }[]
}

partial model PartialOrganization from Organization
// Should expand to:
// model PartialOrganization {
//     details: {
//         name: Name?
//         address: {
//             street: Street?
//             city: City?
//         }?
//     }?
//     branches: {
//         location: PartialAddress?
//         manager: PartialEmployee?
//     }[]?
// }
```

## Additional Declaration Tests

1. Simple partial model declaration:
```taxi
model Person {
    id: PersonId
    name: PersonName
}

partial model PartialPerson from Person
// Should generate equivalent of:
// model PartialPerson {
//     id: PersonId?
//     name: PersonName?
// }
```

2. Invalid declarations (should fail):
```taxi
// No body allowed
partial model BadPerson from Person {
    id: PersonId
}

// Can't use with types
partial type BadType from PersonId

// Must use 'from'
partial model BadSyntax inherits Person

// Can't create partial of a partial
partial model DoublePartial from PartialPerson
```

## Array Tests

3. Array notation equivalence:
```taxi
model Person {
    id: PersonId
    friends: Person[]
}

// These should be equivalent:
operation update1(people: partial Person[])
operation update2(people: Array<partial Person>)

// These should be different:
operation update3(list: partial (Person[]))  // Array itself is optional
operation update4(list: (partial Person)[])  // Array of optional Persons
```

## Inheritance and Type System Tests

4. Type relationships:
```taxi
model Animal {
    id: AnimalId
}

model Dog inherits Animal {
    breed: DogBreed
}

// Should make both inherited and declared fields optional
partial model PartialDog from Dog

// Test that PartialDog is not assignable to Dog
operation needsDog(dog: Dog)
operation takesPartialDog(dog: partial Dog)
```

5. Complex type tests:
```taxi
model Complex {
    id: ComplexId
    nested: {
        field1: String
        field2: Int
    }
    enumField: Status
}

partial model PartialComplex from Complex
// Should handle nested objects and enums correctly
```

## Service and Operation Tests (Not implemented)

6. Operation parameter tests:
```taxi
service PersonService {
    // Inline usage
    operation updatePerson(person: partial Person): Person
    
    // Array usage
    operation updateMany(people: partial Person[]): Person[]
    
    // Mixed usage
    operation complexUpdate(
        required: Person,
        optional: partial Person
    ): Person
}
```

7. Multiple usages (compiler should warn):
```taxi
service MultipleUsage {
    // Should suggest extracting to explicit partial model
    operation update1(person: partial Person)
    operation update2(person: partial Person)
    operation update3(person: partial Person)
}
```

## TaxiQL Tests (Not implemented)

8. Query behavior:
```taxi
// Should work in find queries
find { partial Person }

// Should work with projections
find { Person } as partial Person

// Should work with arrays
find { partial Person[] }

// Should work with filters
find { partial Person(PersonId == '123') }
```

## Edge Cases

9. Empty model test:
```taxi
model Empty { }
partial model PartialEmpty from Empty
```

10. Complex field types:
```taxi
model WithCollections {
    strings: String[]
    maps: Map<String, Int>
    nested: {
        arrays: Person[]
        optional: String?
    }
}

partial model PartialCollections from WithCollections
```

11. Already optional fields:
```taxi
model WithOptional {
    required: String
    optional: String?
}

partial model PartialWithOptional from WithOptional
// Should handle already-optional fields correctly
```

12. Generic type interaction:
```taxi
model Container<T> {
    value: T
}

// Should handle generics correctly
model ApiResponse<T> {
    data: T
    metadata: ResponseMetadata
}

partial model PartialStringResponse from ApiResponse<String>
// Should expand to:
// model PartialStringResponse {
//     data: String?
//     metadata: PartialResponseMetadata?
// }

// Should fail - cannot use with open generics
partial model BadPartialResponse from ApiResponse<T>
```

13. Circular references:
```taxi
model NodeA {
    toB: NodeB
}

model NodeB {
    toA: NodeA
}

partial model PartialNodeA from NodeA
// Should handle circular references correctly
```

## Performance Tests

14. Large model test:
```taxi
model Large {
    field1: Type1
    field2: Type2
    // ... 100+ fields
}

partial model PartialLarge from Large
// Compiler should handle large models efficiently
```

## Documentation Tests

15. Comments and annotations:
```taxi
[[ Documentation for partial model ]]
@Annotation
partial model DocumentedPartial from Person
```

These test cases cover:
- Basic functionality
- Syntax validation
- Type system interactions
- Array handling
- Service/operation usage
- TaxiQL integration
- Edge cases with complex types
- Performance with large models
- Documentation and annotation support
