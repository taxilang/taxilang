# TaxiQL Agent Reference

**Version 1.0.0**
Orbital Engineering
March 2026

> **Note:**
> This document is primarily for AI agents and LLMs to follow when generating,
> maintaining, or debugging TaxiQL queries. Humans may also find it useful,
> but the guidance here is optimised for automation and consistency in
> AI-assisted workflows.

---

## Abstract

Comprehensive reference guide for TaxiQL — the semantic query language used by Orbital to compose data across heterogeneous services. Designed for AI agents and LLMs. Contains 35 rules across 13 categories, ordered from foundational (query syntax, projections, filtering) to advanced (streaming, publishing, troubleshooting). Each rule includes detailed explanations, concrete examples of correct and incorrect usage, and validated test cases.

TaxiQL queries are type-driven: rather than specifying *how* to fetch data, you declare *what types* you need and the engine discovers and orchestrates the appropriate service calls automatically. Understanding this semantic-first design is key to writing correct queries.

---


## Table of Contents

1. [Query Basics](#1-query-basics) — **CRITICAL**
   - 1.1 [Basic query syntax with find and stream](#11-basic-query-syntax-with-find-and-stream)
   - 1.2 [given provides data, it does not filter](#12-given-provides-data-it-does-not-filter)
   - 1.3 [TaxiQL query structure quick reference](#13-taxiql-query-structure-quick-reference)
   - 1.4 [Use :: for deep type traversal](#14-use--for-deep-type-traversal)
2. [Projections](#2-projections) — **CRITICAL**
   - 2.1 [Understand iteration vs transformation in projections](#21-understand-iteration-vs-transformation-in-projections)
   - 2.2 [Understand projection scopes](#22-understand-projection-scopes)
   - 2.3 [Use projections to reshape and transform data](#23-use-projections-to-reshape-and-transform-data)
3. [Filtering](#3-filtering) — **HIGH**
   - 3.1 [Filter calls must be inside find {} braces](#31-filter-calls-must-be-inside-find--braces)
   - 3.2 [Choose between server-side constraints and client-side filtering](#32-choose-between-server-side-constraints-and-client-side-filtering)
4. [Data Discovery](#4-data-discovery) — **HIGH**
   - 4.1 [TaxiQL automatically discovers and enriches data from related services](#41-taxiql-automatically-discovers-and-enriches-data-from-related-services)
   - 4.2 [Use @Id to restrict how a model can be looked up](#42-use-id-to-restrict-how-a-model-can-be-looked-up)
5. [Expressions](#5-expressions) — **HIGH**
   - 5.1 [Use inline expressions to compute values in projections](#51-use-inline-expressions-to-compute-values-in-projections)
   - 5.2 [Format date and time values in projections](#52-format-date-and-time-values-in-projections)
   - 5.3 [Use type-based access in expressions and lambdas, not field names](#53-use-type-based-access-in-expressions-and-lambdas-not-field-names)
   - 5.4 [Understanding expression and projection scoping rules](#54-understanding-expression-and-projection-scoping-rules)
   - 5.5 [Use expression types as traversal nodes with ::](#55-use-expression-types-as-traversal-nodes-with-)
   - 5.6 [Define reusable expression types in the schema](#56-define-reusable-expression-types-in-the-schema)
   - 5.7 [Use when expressions for conditional logic](#57-use-when-expressions-for-conditional-logic)
6. [Standard Library](#6-standard-library) — **MEDIUM**
   - 6.1 [Use allOf, anyOf, noneOf for boolean aggregation](#61-use-allof-anyof-noneof-for-boolean-aggregation)
   - 6.2 [Collection functions in TaxiQL's standard library](#62-collection-functions-in-taxiqls-standard-library)
   - 6.3 [Date and time functions in TaxiQL's standard library](#63-date-and-time-functions-in-taxiqls-standard-library)
   - 6.4 [Math aggregation and rounding functions in TaxiQL's standard library](#64-math-aggregation-and-rounding-functions-in-taxiqls-standard-library)
   - 6.5 [String functions in TaxiQL's standard library](#65-string-functions-in-taxiqls-standard-library)
   - 6.6 [Type transformation functions in TaxiQL's standard library](#66-type-transformation-functions-in-taxiqls-standard-library)
7. [Mutations](#7-mutations) — **HIGH**
   - 7.1 [Write operations require explicit call](#71-write-operations-require-explicit-call)
8. [Streaming](#8-streaming) — **MEDIUM**
   - 8.1 [Use stream for subscribing to event streams](#81-use-stream-for-subscribing-to-event-streams)
9. [Publishing Queries](#9-publishing-queries) — **MEDIUM**
   - 9.1 [Publish named queries as HTTP endpoints](#91-publish-named-queries-as-http-endpoints)
   - 9.2 [Publish named queries as stream processors](#92-publish-named-queries-as-stream-processors)
10. [Query Control](#10-query-control) — **LOW-MEDIUM**
   - 10.1 [Use @Cache for cross-query caching](#101-use-cache-for-cross-query-caching)
   - 10.2 [Control which services the query engine uses](#102-control-which-services-the-query-engine-uses)
11. [Patterns](#11-patterns) — **MEDIUM**
   - 11.1 [Common aggregation and summarization patterns](#111-common-aggregation-and-summarization-patterns)
   - 11.2 [Nested projection with enrichment from services](#112-nested-projection-with-enrichment-from-services)
12. [Advanced Features](#12-advanced-features) — **LOW**
   - 12.1 [Throw errors using casting syntax](#121-throw-errors-using-casting-syntax)
   - 12.2 [User-defined and composed functions in TaxiQL](#122-user-defined-and-composed-functions-in-taxiql)
13. [Troubleshooting](#13-troubleshooting) — **HIGH**
   - 13.1 [Troubleshoot unexpected null values in query output](#131-troubleshoot-unexpected-null-values-in-query-output)

---

## 1. Query Basics

**Impact: CRITICAL**

Everything needed to write a valid TaxiQL query. Covers `find`, `given`, `::` type traversal, and the overall query structure. Start here — all other sections build on this foundation.

---

### 1.1 Basic query syntax with find and stream

**Impact: HIGH (foundation for all TaxiQL queries)**

TaxiQL queries use `find` to fetch data and `stream` to subscribe to event streams. The query engine automatically discovers which services to call based on the types you request.

##### Fetching data

```taxi
// Find all instances of a type
find { Person[] }

// Find a single instance with a server-side constraint
find { Person(FirstName == 'Alice') }

// Find multiple instances with a constraint
find { Person[](LastName == 'Smith') }

// Subscribe to a stream of events
stream { OrderEvent }
```

##### How the query engine works

When you write `find { Person[] }`, the query engine:

1. Looks at available services for operations that return `Person[]`
2. Calls the appropriate operation(s)
3. Returns the results

When you add a constraint like `find { Person(FirstName == 'Alice') }`, the engine:

1. Looks for operations whose output contract matches the constraint
2. Calls only operations that can satisfy the constraint
3. Returns the filtered result

##### Key syntax points

- `Type[]` fetches a collection; `Type` (without brackets) fetches a single instance
- Constraints go in parentheses after the type: `Type(Constraint == value)`
- For collections with constraints: `Type[](Constraint == value)`
- The braces `{}` around the discovery expression are required

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WPP2+DMBDFv8rpliweqlZdkDqkQ6VEVVUpIzA4cAGnxqa2qVohvnvPUBCR6HZ/3rv7vR59UVMjMcHGlqThnZy3BvrMAFyU8+FNNpTAy1yCMjU5FTycglOmijotZ9mr/F8lKxbsq9XuYEJmBhT42ZH7YYRKfdHf75Zsq9kw8aQ5PEEa5wD9mmu316qgnVgx7E6NCjWPxocPdzCILeOzPd/ajtaQn233jzBEV86AmbkoU7J9YYnMrXTsCzzCpB8E+tCduUxzgfTdUhGoPLKaU43cPWS4vM8w4XZEz1BwOWNMizHAtGCYOFtSbJzhIFtHxjg3R6ZMOUZWWXwcSkxMpzVHcfbKwFM7fAIMZCIJEwIAAA==)

```json
{
   "schema": "model Person {\n  firstName: FirstName inherits String\n  lastName: LastName inherits String\n  age: Age inherits Int\n}",
   "query": "given {\n  people: Person[] = [\n    { firstName: 'Alice', lastName: 'Smith', age: 30 },\n    { firstName: 'Bob', lastName: 'Jones', age: 25 }\n  ]\n}\nfind { Person[] }",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n  { \"firstName\": \"Alice\", \"lastName\": \"Smith\", \"age\": 30 },\n  { \"firstName\": \"Bob\", \"lastName\": \"Jones\", \"age\": 25 }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/61QvU7EMAx+FcsTSF1gzAMgFQkG7ramQ0h810DqlCRFh6q+O07vOCRmptj+fvw5C2Y70GhQ4RgdBXjwYYRFM4B3autaB54HSr5kaLlUqPgSSMG+Pr/griTPR82r5kzp01va9LtLvZnGiZIpPjIcqVQ039ye13S9KLHBj5nSl8Q5eHawXCCoyGSSGalQyqiWtcFc5lcpuwWvrs9CEO2Pt4gS5SlKHpl2i0bvNCq4a0DjdkTtNLZsaap6jWsDV9r9H9qTkQtPwunFmOwQW57mgupgQqYGbWTnq4sJL5etNV6/9sI+TWQLuccc+d+j5GLse+tQ8RyC/FOKb7Ls3K7fJJsz3eIBAAA=)

```json
{
   "schema": "model Film {\n  id: FilmId inherits Int\n  title: Title inherits String\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
   "query": "find { Film[] }",
   "parameters": {},
   "stubs": [{"operationName": "getFilms", "response": "[{\"id\": 1, \"title\": \"Inception\"}, {\"id\": 2, \"title\": \"Matrix\"}]"}],
   "expectedJson": "[{\"id\": 1, \"title\": \"Inception\"}, {\"id\": 2, \"title\": \"Matrix\"}]"
}
```

---

### 1.2 given provides data, it does not filter

**Impact: HIGH (misunderstanding given vs constraints is one of the most common query errors)**

`given` statements make data available to the query context. They do **not** filter results or constrain which operations are called. To actually constrain results, use constraints in `find {}` or `.filter()`.

**Incorrect (expecting given to filter results):**

```taxi
// ❌ given makes data AVAILABLE — does NOT filter
given { status: OrderStatus = 'PENDING' }
find { Order[] }
// ↑ May return orders of ANY status. OrderStatus is available
// for service calls that need it, but results are not filtered.
```

**Correct (using constraint or filter to restrict results):**

```taxi
// ✅ Constraint restricts which operations are called
given { status: OrderStatus = 'PENDING' }
find { Order[](OrderStatus == status) }
// ↑ Only returns pending orders

// ✅ Filter restricts results client-side
given {
    orders: Order[] = [
        { id: 'O1', status: 'PENDING', total: 100 },
        { id: 'O2', status: 'SHIPPED', total: 200 }
    ]
}
find { Order[].filter((OrderStatus) -> OrderStatus == 'PENDING') }
// ↑ Only returns pending orders
```

##### Providing data in given

```taxi
// Single value
given { email: EmailAddress = 'jimmy@demo.com' }
find { Customer }

// Object
given {
    customer: Customer = {
        firstName: 'Alice',
        lastName: 'Smith'
    }
}
find { Order[] }

// Array
given {
    friends: Friend[] = [
        { name: 'Jim', age: 20 },
        { name: 'Alice', age: 75 }
    ]
}
find { Friend[].filter((Age) -> Age > 30) }
```

##### Enum values in given

Enums can be provided by name, value, or reference:

```taxi
enum Country { NZ("NZD"), AU("AUD") }

given { codes: Country[] = ["NZD", "AUD"] }   // By value
given { codes: Country[] = ["NZ", "AU"] }      // By name
given { codes: Country[] = [Country.NZ, Country.AU] } // By reference
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42QMW+DMBCF/4p1SxYPgagLUrdELR0CEt0wA8Vu4hZMapuqFeK/94xJaRpUdbLv/B7vffRgqqNoSoigabmoSaK50KRnihDJIz/GnEh1FFpaQzKrpTq4Z2NL25lJko3Dksy2tqwn1aO7z6JYWaYGoPDWCf2JFQ7yXSgf3jr9+et5QW5J7taE9GOvVRKs6HeFVbrbb+P9Ha6muGC9JgO9dIQ/Hdl9nKa77ewIFxybPzNu0OD0BUIw9SwVR+O5r8M6lbpshEUOiPqBgrHdE17zgoL4OInKCv5gWoXgI1tPGEjOIMIzCRhQPH26300F/MPYwe1n0gt/eO2fkH/5w2X/5r/5/i8U4PDK6jXmEKmurpFety/I6MfhC+/XWNxpAgAA)

```json
{
   "schema": "model Order {\n  id: OrderId inherits String\n  status: OrderStatus inherits String\n  total: OrderTotal inherits Int\n}",
   "query": "given {\n  orders: Order[] = [\n    { id: 'O1', status: 'PENDING', total: 100 },\n    { id: 'O2', status: 'SHIPPED', total: 200 },\n    { id: 'O3', status: 'PENDING', total: 50 }\n  ]\n}\nfind { Order[] }",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n  { \"id\": \"O1\", \"status\": \"PENDING\", \"total\": 100 },\n  { \"id\": \"O2\", \"status\": \"SHIPPED\", \"total\": 200 },\n  { \"id\": \"O3\", \"status\": \"PENDING\", \"total\": 50 }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42QwU+DMBTG/5WXd2FL0IwtXkjwtEXxMEjwBhyQdlsVymyL0RD+d1uKMpLFeGrf6/d77+vXoSxPtC7Qx7ohtIJIECqgyzgAI74tQwKMn6hgSkKiBONH8yxVoVo5SpKhuCZTjSqqUfVs7pMo5CrjPbr43lLxpS0c2Qfldnlj9D/T0xwCSE0boBt8OZHnuL8WnHi334b7B90a13mrFfTunFhfEsljGMe77USsrxCbP3fcacDoc/2JjB8YJxoc/d4eWKWoWCwu0lnCzf0srSCYpi7BBHEuRFFTDUr0u95FqdoXfU1zF+nnmZaKkifZcB3VkEYHGTKSoa/PyMvQ1af1a3vjcPswuDb9KZsZv/kvb/+do7FXlG8hQZ+3VaXdi+ZVe7Rl/w07byYKWwIAAA==)

```json
{
   "schema": "model Order {\n  id: OrderId inherits String\n  status: OrderStatus inherits String\n  total: OrderTotal inherits Int\n}",
   "query": "given {\n  orders: Order[] = [\n    { id: 'O1', status: 'PENDING', total: 100 },\n    { id: 'O2', status: 'SHIPPED', total: 200 },\n    { id: 'O3', status: 'PENDING', total: 50 }\n  ]\n}\nfind { Order[].filter((OrderStatus) -> OrderStatus == 'PENDING') }",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n  { \"id\": \"O1\", \"status\": \"PENDING\", \"total\": 100 },\n  { \"id\": \"O3\", \"status\": \"PENDING\", \"total\": 50 }\n]"
}
```

---

### 1.3 TaxiQL query structure quick reference

**Impact: HIGH (foundational reference for understanding overall query anatomy)**

A complete TaxiQL query can include these parts in order:

```
[query QueryName [(params)] {]
    [given { ... }]
    find { Type[](constraints) [.filter(...)] }
    [as { ... projection ... }[] | as NamedType[]]
    [call Service::writeOperation]
[}]
[using { ... } | excluding { ... }]
```

##### Parts breakdown

| Part | Required | Purpose |
|------|----------|---------|
| `query Name { }` | No | Wraps query in a reusable named query |
| `given { }` | No | Provides input data to the query context |
| `find { }` | Yes | Declares what types to find |
| Type constraints `(X == y)` | No | Server-side constraints — only works with matching operations |
| `.filter()` | No | Client-side filtering — always works |
| `as { }[]` | No | Transforms/enriches the result |
| `call Service::operation` | No | Executes a write operation |
| `using { }` / `excluding { }` | No | Controls which services are considered |

##### Common mistakes to avoid

| Mistake | Fix |
|---------|-----|
| `.filter()` outside `find {}` | Move `.filter()` inside the braces |
| Using server-side constraint without matching operation | Use `.filter()` instead, or verify the operation contract |
| `given` used to filter results | Add constraint to `find {}` or use `.filter()` |
| Expecting `A as B[]` to iterate | Split into `A as C[] as B[]` |
| Ambiguous type traversal returning null | Use array syntax, be more specific, or use field names |
| Missing `else` in `when` | Always include an `else` branch |
| Referencing field names in expression types | Expression types can only reference types, not field names |

##### Full example

```taxi
query AffordableInStockProducts {
    find {
        Product[]
            .filter((InStock) -> InStock == true)
            .filter((Price) -> Price < 50)
    } as {
        name: ProductName
        price: Price
        priceRange: String = when {
            Price < 10 -> "Budget"
            Price < 25 -> "Mid-range"
            else -> "Premium"
        }
    }[]
}
```

##### Minimal query

```taxi
// The simplest valid query
find { Customer[] }
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/6VQy07DMBD8FWtPrRSqtsdI5VQE4VAiyq3OwcTb1uDYwXYQKMq_s4lDKY8bJ3t3Z2ZnpwVfHrESkEJlJWp25yQ61nLDmJJpLDPJlDmiU8GzbXDKHPqxDyI0foRsh-IvWLBB6BH10P-_QGssVSU0Nx03Ht2rKnFUG4vBhq3RiaCsYQcMw9hPpqPgriAyJPDSoHunG4aX5WgkbY_YKMLYXhnJ2k_abK90QDeZnLmfsovLb9esVoxDfrVZZ5trDlPWxWW1cKJCYntI2y4BH5pH-u5aOFndEIDsnAwTy6GvLZ1J7V3LQUkOKcnbBYeE3phm7J1W9oMhv76_mM9n8y5h5-Tlb_L2Jsvzq_UP8nIgF-QDy6PNTN0ESPdCe0ygtBRX71ro-9Fkf07RFYR-q7EMKG-9NeT8H8ahD0qUz5mE1DRaU47OPpF4LLsP1jSDpIoCAAA=)

```json
{
  "schema": "model Order {\n  id: OrderId inherits String\n  status: OrderStatus inherits String\n  total: OrderTotal inherits Decimal\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}",
  "query": "query PendingOrders {\n    find { Order[].filter((OrderStatus) -> OrderStatus == \"PENDING\") }\n}",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getOrders",
      "response": "[{\"id\": \"o1\", \"status\": \"PENDING\", \"total\": 100.0}, {\"id\": \"o2\", \"status\": \"SHIPPED\", \"total\": 200.0}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "{\"id\": \"o1\", \"status\": \"PENDING\", \"total\": 100.0}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41STYvbMBD9K4NOCXiDt8UUTFNoKJQUWpbNoYc4B608idWVJVeS-4Hxf-_IchJv0l02PmQ0evNm3tN0zIkKa85yVpsSFdxZU7bCQ1doAM1rzI-pb3QAqSu00jvYeCv1IYAaK8WAor_z_ScUsuYqAKTeeCMec1jH4AxaGaOQ60L3hXZofwWGsdtmPA5zmAYt99JoOKAfAW42P4223REFS9jPFu1fkrKXuoyVMIXA-FvspfJoZ7NxoDncfDgNt1yCty3O_wMfJA7gKPY9ZCnheuDu2O3KsZieejTJ3HN9oHT0Epbwu0J9ZIqzxza3aWhasFVbkgEFu0a8ySLiqyxvbGCdglA5jNd3FmvZ1sdLsr3f7si4hlualUQ6lnd9wpxvHyjcduxkfRBD1k4egOosusbQ09HFtitYUF-wnBp9bBpFYULhoDRkbxdZGhLjPoRUcLpP4EnpZ6NK-M69qC7qszRdvIZgxTV9F8Xp4l12Ubvn5MtVdx4tflL89pnGwToUlVnrpvUsHwgTJowuZbCMq_vRn-Dlrt8R-k-DwmP5xRkdTAvv0MFrnTsvzah03Afok2uiF5VcMp0WI-xEkOU8F4_rkuW6VYoWxJofNHg89v8Az9VOJDYEAAA=)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Decimal\n  inStock: InStock inherits Boolean\n}\nservice ProductService {\n  operation getProducts(): Product[]\n}",
  "query": "find {\n    Product[]\n        .filter((InStock) -> InStock == true)\n        .filter((Price) -> Price < 50)\n} as {\n    name: ProductName\n    price: Price\n    priceRange: String = when {\n        Price < 10 -> \"Budget\"\n        Price < 25 -> \"Mid-range\"\n        else -> \"Premium\"\n    }\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getProducts",
      "response": "[{\"name\": \"Apple\", \"price\": 1.50, \"inStock\": true}, {\"name\": \"Gold Watch\", \"price\": 500.0, \"inStock\": true}, {\"name\": \"Banana\", \"price\": 0.75, \"inStock\": false}, {\"name\": \"Gadget\", \"price\": 30.0, \"inStock\": true}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[\n  { \"name\": \"Apple\", \"price\": 1.50, \"priceRange\": \"Budget\" },\n  { \"name\": \"Gadget\", \"price\": 30.0, \"priceRange\": \"Premium\" }\n]"
}
```

---

### 1.4 Use :: for deep type traversal

**Impact: HIGH (enables decoupled, resilient data access across nested structures)**

The `::` operator performs **deep traversal** — it searches recursively through all nested levels to find a field of the given type. This is preferred over `.` (field name access) because it stays correct when schemas evolve.

##### Type traversal with ::

```taxi
// Schema
model Customer {
    address: Address
}
model Address {
    line1: AddressLine1 inherits String
    postcode: PostCode inherits String
}
```

```taxi
// Find AddressLine1 anywhere within Customer — searches all nesting levels
Customer::AddressLine1

// Chain traversals for more specificity
Customer::Address::PostCode
```

##### Property access with .

The `.` operator navigates by field name. Requires a named variable and exact structural knowledge:

```taxi
// Requires 'customer' to be a named variable in scope
customer.address.line1
```

##### Mixing approaches

You can combine `::` and `.` freely:

```taxi
customer.address::PostCode   // Navigate by field name, then by type
Customer::Address.line1      // Navigate by type, then by field name
```

##### Uniqueness rule

When using `::`, the result must resolve to exactly one field. If multiple fields match (e.g., multiple passengers each have a `PersonName`), the expression is **ambiguous** and returns `null`.

To resolve ambiguity:

1. **Request all instances as an array**: `PersonName[]`
2. **Be more specific with types**: `Customer::HomeAddress::AddressLine1`
3. **Use field names**: `customer.homeAddress.line1`
4. **Use collection functions**: `Address[].getAtIndex(0)::AddressLine1`

**Incorrect (ambiguous — returns null):**

```taxi
given {
    flight: FlightBooking = {
        passengers: [{ name: 'Jim' }, { name: 'Jill' }]
    }
}
find {
    // ❌ Returns null — PersonName is ambiguous (multiple passengers)
    passengerName: PersonName
}
```

**Correct (resolve ambiguity):**

```taxi
find {
    // ✅ Return all as an array
    allNames: PersonName[]

    // ✅ Navigate to a specific item
    firstName: flight.passengers.getAtIndex(0)::PersonName
}
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQP0/DMBDFv4p1ixcPbZmwxFB1AgFCYsQMxj5al+QSbAeBonx3LnETkCjbuz9+73fuIbkD1hY01I3HSuy6lJsao+gNCUG2Rr307rkSgQ4YQ07iMcdA+3HLeh8xJS22RRgaDBW7U6e4VYFwvWzdjtU5u7ZJ2fFrLR5Y7Vj93RpAwXuH8YvB9+EDqSS4E+kPs7gqk/kWua2CQ6lKbyHvZzi53lyIOxuIo6T6xSIvV5v1SophfDlMJ74G8v/90/lDCnhrI29kjAl0PyhIuXth+fSsAD9bdBn9TWqIT5vMDYz2BjSrid6AKv05oMwmQAMlI2Xr3q49aOqqiiNjc2TjUg7fa3BcFPYBAAA=)

```json
{
   "schema": "model Customer {\n  name: CustomerName inherits String\n  address: Address\n}\nmodel Address {\n  line1: AddressLine1 inherits String\n  postcode: PostCode inherits String\n}",
   "query": "given {\n  customer: Customer = {\n    name: 'Alice',\n    address: { line1: '123 Main St', postcode: '90210' }\n  }\n}\nfind {\n  name: CustomerName\n  postcode: PostCode\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "{\n  \"name\": \"Alice\",\n  \"postcode\": \"90210\"\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/21PPU/EMAz9K5aXW/ILIrEwIHEDQmJsOoTW14ZLnV6SIlCV/47b3CFATHl+zvvwiqkbabKocQo9eXjwbhjzfQhnxwOshgFmmxLxQDFpeL7hpjVcDFfRN1sFbCeSryII/CQYHI8UXU7wkqPYihIVXhaKn5I7uHfiKjzt4fpPibu6/F2kqRTAeo07HN10gKL+4b2XReXb7Sl79ZPjvjpb77ea6Wfn/T5pOdsoUxYe9VoUpry8CmxahfQxU5epP4pE7ditDN7MDG4lUVoZVLAB7w1eXVO23fmxR82L9xISw5tY1bF8AZbOShKUAQAA)

```json
{
   "schema": "model FlightBooking {\n  passengers: Passenger[]\n}\nmodel Passenger {\n  name: PersonName inherits String\n}",
   "query": "given {\n  flight: FlightBooking = {\n    passengers: [\n      { name: 'Jim' },\n      { name: 'Jill' }\n    ]\n  }\n}\nfind {\n  allNames: PersonName[]\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "{\n  \"allNames\": [\"Jim\", \"Jill\"]\n}"
}
```

---

## 2. Projections

**Impact: CRITICAL**

How to reshape, rename, and transform query results using `as`. Covers anonymous and named projections, the spread operator, the critical `[]` suffix rule, scope variables, and the distinction between iteration and aggregation. These rules prevent the most common class of query mistakes.

---

### 2.1 Understand iteration vs transformation in projections

**Impact: HIGH (incorrect projection patterns produce wrong results or errors)**

The behavior of a projection depends entirely on the input and output types. This is one of the most important concepts for writing correct projections.

##### Rules

| Projection | Behavior | Description |
|------------|----------|-------------|
| `A[] as B[]` | **Iteration** | Each element of `A[]` is transformed into `B` individually |
| `A as B` | **Transformation** | Single `A` is converted to single `B` |
| `A[] as B` | **Aggregation** | Entire collection is reduced to a single result |
| `A as B[]` | **Transformation** | Single `A` is converted to `B[]` — does **not** iterate |

##### Common mistake: expecting A as B[] to iterate

```taxi
model FilmResponse {
    films: Film[]
}

// ❌ This does NOT iterate — FilmResponse is a single object
find { FilmResponse } as {
    title: Title
}[]

// ✅ Fix: split into two projections — first extract Film[], then iterate
find { FilmResponse } as Film[] as {
    title: Title
}[]
```

The fix works because:
1. `FilmResponse as Film[]` — transforms the response object into the film array
2. `Film[] as { title: Title }[]` — iterates over each film

##### Field-level projections follow the same rules

```taxi
find { Studio } as {
    name: StudioName

    // ❌ FilmResponse is an object, not an array — does not iterate
    films: FilmResponse as (films: Film[]) -> {
        title: Title
    }[]

    // ✅ Fix: explicitly project to Film[] first, then iterate
    films: FilmResponse as Film[] as {
        title: Title
    }[]
}
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQwU7DMAyGX8XyCaRqYtstD4BUJDhsXFDbQ2i8LZAmJUnRpirvjtOVIXaCSxLn9+//k0cM7YE6iQI7p8jAvTYdjLUF0EpMValA2wN5HQOUNmYp6mhIwHO+fsRt9Nrus34i6QW88LkhQzLQ9YhU20D+U7c0RWzn95TrevIyamdhTzGr4eb2TFI17MQCPwbyJybeaatgnCVIIMN5wmKxADq21EdWtQJOS1XDxl562VEkH1CMqcAQh1d+ViNeQp+4gUd/R7PJU+gd4/JvNdaoVY0ClgXUOK0hVzWWNsexv8as5AVkYXW3vEsFXGyrK9uj5J0df3uW6/U6ZVpqD760/RBR7KQJVGDrrNI5RZrNTJXxm9Rw97GnNpJ6CM7OqP/h+ytUiLJ9LxUKOxjDG/XujWPPZfoCXV1LH04CAAA=)

```json
{
   "schema": "model Film {\n  id: FilmId inherits Int\n  title: Title inherits String\n  year: YearReleased inherits Int\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
   "query": "find { Film[] } as {\n  title: Title\n  year: YearReleased\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getFilms", "response": "[{\"id\": 1, \"title\": \"Inception\", \"year\": 2010}, {\"id\": 2, \"title\": \"Matrix\", \"year\": 1999}]"}],
   "expectedJson": "[{\"title\": \"Inception\", \"year\": 2010}, {\"title\": \"Matrix\", \"year\": 1999}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WPMU/DMBCF/8rploDkoVI3SwwVElIRYumIGYxzTQ3JpdgXBIry37kkTcnCdva9e+97PeZwosajxaYtqYb7LkvbUILeMcAxpizPviELD8sIkU+UomQ4SIpcjbraL7In/7/KVyrYVavdnsXxgAY/O0o/ClHFL+I5O1xI7B/THfRrpGJXx0CFWcUXhybKSb+mrO0GBvV3fIxc6u3VaACfLw27up5PZ07NCC0HLzfXwgYKUMel2e26ykx/9kkXQimj7QeDWbo3HV9eDdL3mYJQ+Zhb1n69wyXRoQWHUwWYqB0a/VDncbPdjMZZfPjYl2hZjzQnte/qNj+HX0qSz1y8AQAA)

```json
{
   "schema": "model Customer {\n  firstName: FirstName inherits String\n  lastName: LastName inherits String\n  age: Age inherits Int\n}",
   "query": "given {\n  customer: Customer = { firstName: 'Alice', lastName: 'Smith', age: 30 }\n}\nfind { Customer } as {\n  fullName: String = concat(FirstName, ' ', LastName)\n  age: Age\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "{\"fullName\": \"Alice Smith\", \"age\": 30}"
}
```

---

### 2.2 Understand projection scopes

**Impact: MEDIUM (controls what data is visible during transformation)**

Scopes control what data is visible during a projection. The object being transformed is automatically in scope, but you can name it explicitly for field access.

##### Implicit scope

The object being transformed is automatically in scope:

```taxi
// Each Customer in the array is in scope for its projection
find { Customer[] } as {
    firstName: FirstName   // Resolved from current Customer
    lastName: LastName
}[]
```

##### Named scope variables

Explicitly name the item in scope for field-level access:

```taxi
find { Customer[] } as (customer: Customer) -> {
    upperName: String = upperCase(customer.name)
    city: City  // Still resolved from customer automatically
}[]
```

Use named scope when you need to access a field by name (`.fieldName`) rather than by type.

##### Critical mistake: `as (Type[]) ->` does NOT iterate

A common error is using a type array scope modifier expecting it to iterate over the collection:

```taxi
// ❌ WRONG — does NOT iterate over Actor[]
// This puts Actor[] in scope as an aggregate, but it does NOT yield one projection per actor
find { Film } as (Actor[]) -> {
    actorName: ActorName   // null — Actor is not individually iterated
}[]
```

If you want to iterate over a related collection, use chained projections:

```taxi
// ✅ Correct — chain projections to iterate
find { Film } as Actor[] as {
    actorName: ActorName   // Each Actor is in scope individually
}[]
```

See also: [projections-iterating-vs-transforming.md](../rules/projections-iterating-vs-transforming.md) for a full explanation of the iteration rules.

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22QzU7DMBCEX2W154gHiITEjzjAgQvHOAcnWVqDf4LtVK2svDtrnFYNaS6Ox57Z+Zww9HsyEms0biANz1OIzpCHJCyAlYbqi/bOO1B2T17FAB_RK7vLt9ivdA0vedmez1jhz0T-xDN26kC2RMMltmnhHpqi5S8tYwU-atWTwOo8QaDMygMdpRk13fXOCIS5uuV9ct3K2bnuv6_YWq4o7KeyA7uvOs0gw7nq9h2Kfk3OMU3LrKP0fB7JB6zTXGGIU8e_TVshHUfqIw1vwVl-jcKcBOZ4gStigX_ZRb1BvUCvzAvyyrrBZtbcMkTZf78OWNtJay7t3RdXK9v5F2JFxfYWAgAA)

```json
{
  "schema": "model Customer {\n  name: CustomerName inherits String\n  email: Email inherits String\n}",
  "query": "given {\n    Customer[] = [\n        { name: \"Alice\", email: \"alice@example.com\" },\n        { name: \"Bob\", email: \"bob@example.com\" }\n    ]\n}\nfind { Customer[] } as {\n    name: CustomerName\n    email: Email\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"name\": \"Alice\",\n      \"email\": \"alice@example.com\"\n   },\n   {\n      \"name\": \"Bob\",\n      \"email\": \"bob@example.com\"\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/21QQU7DMBD8ympPVAo8IBJIpOLQqgIkjnEObrK0hsQOtoOoIv-ddZ2mFcUHyzue3ZmdEV29p05ijp1pqIXl4LzpyMIoNICWHeUz9swVKL0nq7yDN2-V3kVWrfyBWXxf_wbM8Gsge2CFnfomnQbDPLSs4B7KhMUzTqICH1tVk8Bsmi9wY3RjtEAI2X_8wmwv2a_SKhfJiVuxF6HflW645UI8gHRwU0_AedkF3D6cvA59n7bPp73Y8hFbSkdz7100skgd50hYtqw4hF5a_vZkHeZjyND5YcvPssqQfnqqPTVrZzTHVI4CZ0WBxyg2q-VTXE5gnJzAUxwhg-uO4qX4y58CCdGN87L-XDWY66Ft2Zw1H2whleEXbCNeXxUCAAA=)

```json
{
  "schema": "model Customer {\n  name: CustomerName inherits String\n  city: City inherits String\n}",
  "query": "given {\n    Customer[] = [\n        { name: \"Alice\", city: \"London\" },\n        { name: \"Bob\", city: \"Paris\" }\n    ]\n}\nfind { Customer[] } as (customer: Customer) -> {\n    upperName: String = upperCase(customer.name)\n    city: City\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"upperName\": \"ALICE\",\n      \"city\": \"London\"\n   },\n   {\n      \"upperName\": \"BOB\",\n      \"city\": \"Paris\"\n   }\n]"
}
```

---

### 2.3 Use projections to reshape and transform data

**Impact: HIGH (projections are TaxiQL's primary mechanism for defining output structure)**

Projections use the `as` keyword to transform and reshape data. They define what fields to include, compute new values, and enrich data from other services.

##### Anonymous type projections

Define the target structure inline:

```taxi
find { Purchase[] } as {
    txn: TransactionId
    customerName: CustomerName
    total: OrderTotal
}[]
```

##### Named type projections

Project to an existing model definition:

```taxi
model CustomerTransaction {
    transactionId: TransactionId
    name: CustomerName
    price: Price
}

find { Purchase[] } as CustomerTransaction[]
```

##### Extending existing models

Add fields to an existing model without modifying it:

```taxi
find { Book[] } as BookDatabaseRecord {
    // All BookDatabaseRecord fields are included
    rottenTomatoesReview: RottenTomatoesReview  // Plus this extra field
}[]
```

##### Field shorthand

When a source field name matches what you want, the type is inferred:

```taxi
model Order {
    id: OrderId
    status: OrderStatus
    total: OrderTotal
}

find { Order[] } as {
    id        // Infers OrderId
    status    // Infers OrderStatus
    // total is excluded
}[]
```

##### Spread operator

Include all fields, optionally excluding specific ones:

```taxi
// Include all fields plus a new one
find { Order[] } as {
    tracking: TrackingNumber
    ...
}[]

// Include all except specific fields
find { Order[] } as {
    ... except { internalNotes, auditLog }
}[]
```

**Note:** The spread operator (`...`) must be the **last** entry in the field list.

##### Important: array brackets

When projecting a collection, the `[]` at the end of the projection is critical:

```taxi
// ✅ Correct — iterates over each Film
find { Film[] } as {
    title: Title
}[]

// ❌ Incorrect — aggregates instead of iterating (missing [])
find { Film[] } as {
    title: Title
}
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQwU7DMAyGX8XyCaRqYtstD4BUJDhsXFDbQ2i8LZAmJUnRpirvjtOVIXaCSxLn9+//k0cM7YE6iQI7p8jAvTYdjLUF0EpMValA2wN5HQOUNmYp6mhIwHO+fsRt9Nrus34i6QW88LkhQzLQ9YhU20D+U7c0RWzn95TrevIyamdhTzGr4eb2TFI17MQCPwbyJybeaatgnCVIIMN5wmKxADq21EdWtQJOS1XDxl562VEkH1CMqcAQh1d+ViNeQp+4gUd/R7PJU+gd4/JvNdaoVY0ClgXUOK0hVzWWNsexv8as5AVkYXW3vEsFXGyrK9uj5J0df3uW6/U6ZVpqD760/RBR7KQJVGDrrNI5RZrNTJXxm9Rw97GnNpJ6CM7OqP/h+ytUiLJ9LxUKOxjDG/XujWPPZfoCXV1LH04CAAA=)

```json
{
   "schema": "model Film {\n  id: FilmId inherits Int\n  title: Title inherits String\n  year: YearReleased inherits Int\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
   "query": "find { Film[] } as {\n  title: Title\n  year: YearReleased\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getFilms", "response": "[{\"id\": 1, \"title\": \"Inception\", \"year\": 2010}, {\"id\": 2, \"title\": \"Matrix\", \"year\": 1999}]"}],
   "expectedJson": "[{\"title\": \"Inception\", \"year\": 2010}, {\"title\": \"Matrix\", \"year\": 1999}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQwU7DMAyGX8XyCaRqYtstD4BUJDhsXFDbQ2i8LZAmJUnRpirvjtOVIXaCSxLn9+//k0cM7YE6iQI7p8jAvTYdjLUF0EpMValA2wN5HQOUNmYp6mhIwHO+fsRt9Nrus34i6QW88LkhQzLQ9YhU20D+U7c0RWzn95TrevIyamdhTzGr4eb2TFI17MQCPwbyJybeaatgnCVIIMN5wmKxADq21EdWtQJOS1XDxl562VEkH1CMqcAQh1d+ViNeQp+4gUd/R7PJU+gd4/JvNdaoVY0ClgXUOK0hVzWWNsexv8as5AVkYXW3vEsFXGyrK9uj5J0df3uW6/U6ZVpqD760/RBR7KQJVGDrrNI5RZrNTJXxm9Rw97GnNpJ6CM7OqP/h+ytUiLJ9LxUKOxjDG/XujWPPZfoCXV1LH04CAAA=)

```json
{
   "schema": "model Film {\n  id: FilmId inherits Int\n  title: Title inherits String\n  year: YearReleased inherits Int\n}\nmodel FilmSummary {\n  title: Title\n  year: YearReleased\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
   "query": "find { Film[] } as FilmSummary[]",
   "parameters": {},
   "stubs": [{"operationName": "getFilms", "response": "[{\"id\": 1, \"title\": \"Inception\", \"year\": 2010}, {\"id\": 2, \"title\": \"Matrix\", \"year\": 1999}]"}],
   "expectedJson": "[{\"title\": \"Inception\", \"year\": 2010}, {\"title\": \"Matrix\", \"year\": 1999}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQwU7DMAyGX8XyCaRqYtstD4BUJDhsXFDbQ2i8LZAmJUnRpirvjtOVIXaCSxLn9+//k0cM7YE6iQI7p8jAvTYdjLUF0EpMValA2wN5HQOUNmYp6mhIwHO+fsRt9Nrus34i6QW88LkhQzLQ9YhU20D+U7c0RWzn95TrevIyamdhTzGr4eb2TFI17MQCPwbyJybeaatgnCVIIMN5wmKxADq21EdWtQJOS1XDxl562VEkH1CMqcAQh1d+ViNeQp+4gUd/R7PJU+gd4/JvNdaoVY0ClgXUOK0hVzWWNsexv8as5AVkYXW3vEsFXGyrK9uj5J0df3uW6/U6ZVpqD760/RBR7KQJVGDrrNI5RZrNTJXxm9Rw97GnNpJ6CM7OqP/h+ytUiLJ9LxUKOxjDG/XujWPPZfoCXV1LH04CAAA=)

```json
{
   "schema": "model Film {\n  id: FilmId inherits Int\n  title: Title inherits String\n  year: YearReleased inherits Int\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
   "query": "find { Film[] } as {\n  ... except { id }\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getFilms", "response": "[{\"id\": 1, \"title\": \"Inception\", \"year\": 2010}, {\"id\": 2, \"title\": \"Matrix\", \"year\": 1999}]"}],
   "expectedJson": "[{\"title\": \"Inception\", \"year\": 2010}, {\"title\": \"Matrix\", \"year\": 1999}]"
}
```

---

## 3. Filtering

**Impact: HIGH**

Two filtering mechanisms — server-side constraints (in `find {}`) and client-side `.filter()` — with clear rules for choosing between them. Knowing which to use affects both correctness and performance.

---

### 3.1 Filter calls must be inside find {} braces

**Impact: HIGH (placing filter outside braces causes syntax errors)**

The `.filter()` call is part of the discovery expression and must be **inside** the `find {}` braces. Placing it outside is a syntax error.

**Incorrect (filter outside the braces):**

```taxi
// ❌ Syntax error — filter is outside find {}
find { Person[] }.filter((Age) -> Age < 20)
```

**Correct (filter inside the braces):**

```taxi
// ✅ Correct — filter is inside find {}
find {
    Person[].filter((Age) -> Age < 20)
}
```

##### Lambda parameters should use types

Prefer type-based access in lambda predicates — it stays correct even if fields are renamed:

```taxi
// ✅ Preferred: type-based — decoupled from field name
find {
    Order[].filter((OrderStatus) -> OrderStatus == 'pending')
}

// ⚠️ Less preferred: field-based — coupled to field name
find {
    Order[].filter((order:Order) -> order.status == 'pending')
}
```

##### Multiple conditions

Combine conditions with `&&` and `||`:

```taxi
find {
    Customer[].filter((Customer) ->
        Customer::FirstName == 'Jimmy' && Customer::Age > 21
    )
}
```

##### Chained filters

Multiple `.filter()` calls can be chained:

```taxi
find {
    Product[]
        .filter((InStock) -> InStock == true)
        .filter((Price) -> Price < 30)
}
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2WPQUvDQBCF/8owl7awilUECbRQPdWDCB6zOWyTabK62cTNRpSQ/+5skgZbb29mHm++12GTFlQqjLCsMjLwSq6pLHTSAlhVUjRtXliDtgU57Rt4807bPHhUzpZd/ue2t17aHgV+tuR+ODjXXzQl1lTVZs6ME9hAHPYA3fRtsTM6pYUYg+9uoBcXhsfqcDqv7/+fnwrljJ4TbtkSHAkzSXvUNhtJTgTXR208ueWSO6zgajt02W5g/bAaW9TKcTBbGoy6XmDj2wPLOBFI3zWlnrJnTuKeQ5MOJAYSiRGroYxEwZJpwm5udG6cqM+sI3qC4adKP/YZRrY1hpFc9c6Px7H/Bc6EFXXDAQAA)

```json
{
   "schema": "model Person {\n  name: PersonName inherits String\n  age: Age inherits Int\n}",
   "query": "given {\n  people: Person[] = [\n    { name: 'Alice', age: 30 },\n    { name: 'Bob', age: 15 },\n    { name: 'Charlie', age: 25 }\n  ]\n}\nfind {\n  Person[].filter((Age) -> Age >= 18)\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n  { \"name\": \"Alice\", \"age\": 30 },\n  { \"name\": \"Charlie\", \"age\": 25 }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41RTU/DMAz9K1Eu26SABohLRTkgJDQOCGlIHJYeQuq12dqkpCliqvrfyce2dmUSnOLYfvZ7zy2ueQ4lwxEuVQoFetUqbbhBLZUISVZCdEi92A8SMgctTI2WRguZuaZKC+677NPXF9K4opBLo/g2sn8f9A0PShXAJJUdJvizAb2zHDLxBTLsrsLW+rh/laAYrVwJoXZPbfIu0gzMhBxYXM1Jv9PoBlBHRpAndgq5vv0T8qhULvgWdj1qCFqzoj6DesutQ6xkG5ENCP7e5mCJ9YHKtZBpUH/UHGZerkVhQE+nextn6OL+aGkc+0GzUas/iG8Mp7lDN/NZsLti2lK0XTWO2o7g2jQfNlwlBMN3BdxA+lwraQ/i/W4RxU4TxZGNguUUExt7US7tfKd4L8wlBkaewoP9I7i7wf/gA1PHFM7PoDLBTiHj20WKI9kUhTVAq42VGb7dD1y1+68FAwAA)

```json
{
   "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Int\n  inStock: InStock inherits Boolean\n}",
   "query": "given {\n  products: Product[] = [\n    { name: 'Widget', price: 10, inStock: true },\n    { name: 'Gadget', price: 25, inStock: true },\n    { name: 'Doohickey', price: 5, inStock: false },\n    { name: 'Thingamajig', price: 15, inStock: true }\n  ]\n}\nfind {\n  Product[]\n    .filter((InStock) -> InStock == true)\n    .filter((Price) -> Price < 30)\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n  { \"name\": \"Widget\", \"price\": 10, \"inStock\": true },\n  { \"name\": \"Gadget\", \"price\": 25, \"inStock\": true },\n  { \"name\": \"Thingamajig\", \"price\": 15, \"inStock\": true }\n]"
}
```

---

### 3.2 Choose between server-side constraints and client-side filtering

**Impact: HIGH (wrong choice can cause missing results or unnecessary data transfer)**

There are two approaches to filtering in TaxiQL, and choosing the right one matters for correctness and performance.

##### Server-side constraints

Constraints in the `find {}` block control **which operations are called**. The engine only calls operations whose declared contract can satisfy the constraint.

```taxi
// Only calls operations that can filter by FirstName
find { Customer(FirstName == 'Jimmy') }

// Multiple constraints with &&
find { Customer[](FirstName == 'Jimmy' && Age > 30) }
```

**Important:** Server-side constraints only work if a matching operation exists in the schema:

```taxi
service CustomerService {
    // Explicit contract: declares which input maps to which output field
    operation findByName(
        @PathVariable first: FirstName,
        @PathVariable last: LastName
    ): Customer(FirstName == first && LastName == last)

    // Shorthand contract: (...) means "all inputs match on the returned object"
    // Equivalent to the explicit version above — the engine infers the mappings
    operation findByName(
        @PathVariable first: FirstName,
        @PathVariable last: LastName
    ): Customer(...)
}
```

If no operation matches the constraint, the query will fail or return no results.

##### Client-side filtering

Use `.filter()` when you want to fetch data first and then filter locally:

```taxi
// Fetch all customers, then filter locally
find { Customer[].filter((Age) -> Age > 21) }
```

##### How to choose

1. **Check the schema** for operations with matching output contracts
2. If a matching operation exists → use a **server-side constraint**: `find { Type(Constraint == value) }`
3. If no matching operation exists → use **client-side filtering**: `find { Type[].filter(...) }`
4. **Never assume** a server-side constraint will be satisfied — always verify against the schema

**Incorrect (assuming a server-side constraint works without verifying):**

```taxi
// ❌ If no operation declares it can filter by Age, this fails
find { Customer[](Age > 21) }
```

**Correct (using client-side filter when no matching operation exists):**

```taxi
// ✅ Fetches all customers, then filters locally
find { Customer[].filter((Age) -> Age > 21) }
```

##### Supported operators

| Symbol | Meaning |
|--------|---------|
| `==` | Equal to |
| `!=` | Not equal to |
| `>` | Greater than |
| `>=` | Greater than or equal to |
| `<` | Less than |
| `<=` | Less than or equal to |
| `&&` | Logical AND |
| `\|\|` | Logical OR |
| `in` | Contained within a list |
| `!in` | Not contained in a list |

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WPQU_DMAyF_0rk0yYVtHWakCoxaew0Dlw4Nj1kbdYGUnckKQJV_e-4y2gnMXKK7c_P73Xo8krVEhOsm0IZ2LXON7Wy0AkCIFmrZOy9cAWaKmW1d_DqraZyoGTJ0La8mu3JC-oxwo9W2W8WL_WnoqAJo16awSOkoTe87nJP4NboXAmMgnS8hj66hT01hxFaPvwD7SppjZ7UVgvoA5exR0FHTQXjk6n7ozZe2dmMI83hbnOOtoF4OYch0klaVmbAYdL1ETrfHvibZhGqr5PKvSqeXUMcOkTrfk0JHCwJvA44zdjbMIrX59Ylys3dKdCf7dUibAvKcHAm8_d9gQm1xrBx27yxvVD2P3wJdWr6AQAA)

```json
{
  "schema": "model Customer {\n  name: CustomerName inherits String\n  age: Age inherits Int\n}",
  "query": "given {\n    Customer[] = [\n        { name: \"Alice\", age: 25 },\n        { name: \"Bob\", age: 17 },\n        { name: \"Charlie\", age: 30 }\n    ]\n}\nfind { Customer[].filter((Age) -> Age > 21) }",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"name\": \"Alice\",\n      \"age\": 25\n   },\n   {\n      \"name\": \"Charlie\",\n      \"age\": 30\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/52RTWuEMBCG_StDTrtgxRakIGyhH5f2UAp73HhI47imG6ONsXQR_3sTo-u23aVQczCZyTt55p0OGl5gyYCgqjKk5EVXWcsN6agCotoqM4VeTQagqwl73UHXRgu1dZc4M7ittD4h96PuZK3aC97Xsr85_4BclExS1UMAr52ovQXZig9UHoBMj29SsiIbH3JfN7IRuK1ridSCIw4KuW6FccHx2Ysxjkgf3NKvDbLdj3yJ7Js8DqNz-jumrPsLIAqv4_MATJaV-lVANMWRfioaCLw-tX5RlQuVGTPHgcJcSIN6sZjGsCQXN_MMVquZawnkd920RQCotIOk6wNo0ft6u5kEAD-r5Aazp6ZSdiLe-G6ip-DYIYZ4PYemLnx-MmLODx25pB3KEB1tOVn-4O4_6jvP_QNUpeD6Y3z3mEGiWilt-7p6s037Y_8FoHVkJwkDAAA=)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  category: Category inherits String\n  price: Price inherits Decimal\n}",
  "query": "given {\n    Product[] = [\n        { name: \"Apple\", category: \"fruit\", price: 1.50 },\n        { name: \"Steak\", category: \"meat\", price: 15.00 },\n        { name: \"Banana\", category: \"fruit\", price: 0.75 },\n        { name: \"Salmon\", category: \"fish\", price: 20.00 }\n    ]\n}\nfind { Product[].filter((Category) -> Category == \"fruit\") }",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"name\": \"Apple\",\n      \"category\": \"fruit\",\n      \"price\": 1.5\n   },\n   {\n      \"name\": \"Banana\",\n      \"category\": \"fruit\",\n      \"price\": 0.75\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42QQWvDMAyF_4rQpQ2EMtgtUFi3U3cYY4Vd5h7cRE3cOXJmO2Ml5L9Pbku6wQ67WU9Pn548YCgbajUW2LqKLDz0IbqWPAyKAVi3VEzak1RguCFvYoBN9IbrJNK1mFb1j96ao-JRcSD_aUqaCJtLfYK7jryOxjHsDVf3x4SfpwbA3bOOzav2Ru8s_ZEiubKrNF8sFpksxBw_evJHuSYhYbg6ft2wXMJsZSXILIM01GkvciQfsBjGHEPsd_J8G3DKmOYu2HNSGfMUOic3ij4oTCkVFqDwhFaYy1O-Jmm3N2kNlY1bc9dHLPbaBsqxdFyZhNf25QJLe7fjVtxfHZWRqsfg-L8bQtTl-7rCgntr5S7vDsI4l-M3u-nUZ-wBAAA=)

```json
{
  "schema": "model Customer {\n  name: CustomerName inherits String\n  age: Age inherits Int\n}\nservice CustomerService {\n  operation findByName(\n    @PathVariable name: CustomerName\n  ): Customer(...)\n}",
  "query": "find { Customer(CustomerName == 'Alice') }",
  "parameters": {},
  "stubs": [
    {"operationName": "findByName", "response": "{\"name\": \"Alice\", \"age\": 30}"}
  ],
  "expectedJson": "{\"name\": \"Alice\", \"age\": 30}"
}
```

---

## 4. Data Discovery

**Impact: HIGH**

TaxiQL's signature capability: automatically calling services to populate fields not present in the source data. Covers how discovery works, how `@Id` restricts which operations are eligible, and `@FirstNotEmpty` for fallback behaviour. Understanding this unlocks cross-service data composition without manual orchestration.

---

### 4.1 TaxiQL automatically discovers and enriches data from related services

**Impact: HIGH (core feature that enables cross-service data composition without manual orchestration)**

When evaluating a projection, TaxiQL resolves values in this order:

1. **Source object** currently in scope
2. **Other data** already available in the query context
3. **Service calls** to fetch missing data

This means you can request types that aren't on the source model — TaxiQL will automatically find and call services to populate them.

##### How it works

```taxi
// Schema
model Purchase {
    customerId: CustomerId
    amount: Money
}
model Customer {
    @Id customerId: CustomerId
    name: CustomerName
}
service CustomerService {
    operation getCustomer(CustomerId): Customer
}
```

```taxi
// CustomerName is not on Purchase, but TaxiQL discovers it:
// 1. Purchase has CustomerId
// 2. CustomerService.getCustomer accepts CustomerId, returns Customer
// 3. Customer has CustomerName
// → TaxiQL calls getCustomer automatically
find { Purchase[] } as {
    amount: Money
    customerName: CustomerName  // Discovered via CustomerId → getCustomer
}[]
```

##### The @Id attribute

`@Id` marks a field as the **primary identifier** for a model. When a model has an `@Id` field, the query engine **restricts** discovery to only those operations that accept the `@Id` type as input.

This is a **restriction**, not a preference — operations that don't accept the `@Id` type are excluded entirely.

```taxi
model Trade {
    @Id id: TradeId
    traderName: TraderName
}
service TradeApi {
    operation getTradeById(TradeId): Trade         // ✅ Eligible — accepts @Id type
    operation getTradeByTicker(TradeTicker): Trade // Excluded — does not accept TradeId
}
```

This prevents the engine from calling semantically wrong operations — for example, calling `getTradeByTicker` to fetch a trader name, which would return data about a different trade entity.

See also: [discovery-id-based-lookup.md](./discovery-id-based-lookup.md) for detailed examples.

##### @FirstNotEmpty

Instructs TaxiQL to try other services if the primary source returns null:

```taxi
find { Person[] } as {
    name: PersonName
    @FirstNotEmpty
    email: EmailAddress  // If null from Person, try other services
}[]
```

##### Key principles

- Simply request the type you need in the projection — TaxiQL handles the orchestration
- When a model has `@Id`, only operations accepting that `@Id` type can be used to fetch it
- When a model has no `@Id`, any available input in scope can be used as an operation argument
- If multiple services could provide a value, TaxiQL chooses the shortest path
- Use `@FirstNotEmpty` for fallback behavior across multiple services
- Use `using {}` or `excluding {}` to control which services are considered

##### Discovery in expressions

Expressions can reference types not present on the source object:

```taxi
model Order {
    basePrice: Price
    customerId: CustomerId
}

// DiscountRate is not on Order — TaxiQL will call services to find it
find { Order[] } as {
    price: Price
    finalPrice: Decimal = Price * (1 - DiscountRate)
}[]
```

##### Validation

Note: The validation harness uses a fixed stub response for `getCustomer` since conditional response matching with discovery is currently under investigation (see `problems/discovery-conditional-responses.md`).

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WRMU_DMBCF_4p1UytFqF09gWApEgjRMclgnGtjcOxgO4gq8n_nojRJA1VRs8Rnn9_33rkFL0usBHCobIGavTROlsIjazPD6JOND7ZCtyk4ux_XTJkSnQqebYNTZt_3iso2JnD2ZA0eppYHlKoSOjMxMz1kEBogt6R4HjQ38SwqnE676q8Rgnh0X0rimGV7rI80W6MTQVnD9hiGHr9Y8vFCms90BuAlnaFnMXlfTlZJDhL4bNAdaNI7ZQrWntBYZMLTzmyAyaXYMc1JsBaOioDOA29jAj40b7RMWxi9dd2EPE1KFx362lI-OknbDKbZZ8AZ1esMEvr3frq99Wp1s4odE2VpN6ZuAvCd0B4TkNYUqmMJ_XrU7UzkMTnrY8gxt3HBxekY-pM7Tc-QQbzOTk7d3zXKgMWjt6aH_or4L88HIT82BXDTaE0v4Ow7KfZl_AHm01qMTgMAAA==)

```json
{
  "schema": "model Purchase {\n    customerId: CustomerId inherits String\n    amount: Money inherits Decimal\n}\nmodel Customer {\n    @Id customerId: CustomerId\n    customerName: CustomerName inherits String\n}\nservice PurchaseService {\n    operation getPurchases(): Purchase[]\n}\nservice CustomerService {\n    operation getCustomer(CustomerId): Customer\n}",
  "query": "find { Purchase[] } as { amount: Money, customerName: CustomerName }[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getPurchases",
      "response": "[{\"customerId\": \"c1\", \"amount\": 100.0}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getCustomer",
      "response": "{\"customerId\": \"c1\", \"customerName\": \"Alice\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "{\"amount\": 100.0, \"customerName\": \"Alice\"}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WSwW6DMAyGXyXKqZO4bMecxi5Vp2md1t6AQxbcNiskLAnTKpR3n1NgpWMgjQt28tv-_ENDrThAySmjpc6hIFvDc9jUZcnNiTSpIvi4cGZWOWtvMSJSHcBIZ8nGGan2nU6KI5hOtT0nY6FP1WBSP-Iee47HDMc_8xL6mxD_2dmC-ZQCWllcyb69rsBwJ7Uie3CDFSXYxQ27WjrJpkoeTu1Oi8F-ffFMTbfLog8uJZ5G9KMGc0L3d1LlpPlFQjzhdvorzNmD3ZMM-1fcYObAWMoaH1Hr6jcMk4b-wAY5EoycwWoDttLoKl4nTUp7hJQygtltSqPwPjvRnsXxy1NKfUTG6ruxerleL1EdOEEc9EpVtaNsxwsLERVa5TLw8eK1wwjgmY-m2S92X8NPs18Ma_kL_H0Q6X9EGaq_KhAO8ker1bxbExPnLJsoCb5Zx8UR12WqLgr83Ea_I0eb-m9Ur6dB3gMAAA==)

```json
{
  "schema": "model Order {\n  orderId: OrderId inherits String\n  customerId: CustomerId inherits String\n}\nmodel Customer {\n  @Id customerId: CustomerId\n  name: CustomerName inherits String\n  email: Email inherits String\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}\nservice CustomerService {\n  operation getCustomer(CustomerId): Customer\n}",
  "query": "find { Order[] } as {\n    orderId: OrderId\n    customerName: CustomerName\n    customerEmail: Email\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getOrders",
      "response": "[{\"orderId\": \"ord-001\", \"customerId\": \"cust-1\"}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getCustomer",
      "response": "{\"customerId\": \"cust-1\", \"name\": \"Alice Smith\", \"email\": \"alice@example.com\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "{\"orderId\": \"ord-001\", \"customerName\": \"Alice Smith\", \"customerEmail\": \"alice@example.com\"}"
}
```

---

### 4.2 Use @Id to restrict how a model can be looked up

**Impact: HIGH (prevents incorrect cross-entity data pollution)**

When a model declares an `@Id` field, TaxiQL uses it as a **strict constraint**: only operations that accept the `@Id` type as input are eligible to fetch that model during discovery.

Without `@Id`, any available input from the current scope can be used to call an operation returning that model — which can lead to wrong data being fetched.

##### The problem @Id solves

Consider a `TradeSummary` with both a `TraderId` and a `TradeTicker`. If you want to enrich with `TraderName` from a `Trade` model, two operations are available:

- `getTradeByTicker(TradeTicker): Trade` — looks up trade by market symbol
- `getTradeById(TraderId): Trade` — looks up trade by trader ID

Without `@Id`, the query engine might use `getTradeByTicker` to fetch a `Trade` and extract `TraderName` — but that `Trade` is a *different* entity (the latest trade with that ticker, not the trader's own record).

`@Id` tells the engine: **"only look up this model using its primary identifier"**.

##### Without @Id — any available input is usable

```taxi
model Trade {
    traderId: TraderId     // No @Id — any available input can be used
    traderName: TraderName
}
service TradeApi {
    operation getTradeByTicker(TradeTicker): Trade  // eligible
    operation getTradeById(TraderId): Trade          // also eligible
}
```

Either operation can be called to resolve `TraderName`. The engine picks the shortest path, which may not be the semantically correct one.

##### With @Id — only the identifier-accepting operation is eligible

```taxi
model Trade {
    @Id traderId: TraderId   // Restricts lookup to operations accepting TraderId
    traderName: TraderName
}
service TradeApi {
    operation getTradeByTicker(TradeTicker): Trade  // excluded — does not accept TraderId
    operation getTradeById(TraderId): Trade          // only eligible operation
}
```

Now `getTradeByTicker` is excluded from discovery when resolving `Trade`. Only `getTradeById` can be called.

##### Incorrect (no @Id — wrong operation may be called)

```taxi
// Trade has no @Id — getTradeByTicker could be used,
// which fetches a Trade by market symbol, not by trader identity.
model Trade {
    traderId: TraderId
    traderName: TraderName
}
service TradeApi {
    operation getTradeByTicker(TradeTicker): Trade
    operation getTradeById(TraderId): Trade
}
```

##### Correct (with @Id — only semantically correct operation used)

```taxi
// @Id ensures only getTradeById(TraderId) can be used to fetch Trade
model Trade {
    @Id traderId: TraderId
    traderName: TraderName
}
service TradeApi {
    operation getTradeByTicker(TradeTicker): Trade  // excluded from discovery
    operation getTradeById(TraderId): Trade          // the only eligible path
}
```

##### Key rules

- `@Id` restricts lookup to operations that accept the `@Id` type as input
- Without `@Id`, any available input in scope can be used as an operation argument
- Models without `@Id` are more flexible but can lead to semantically incorrect enrichment
- Only the top-level `@Id` on an entity is considered — nested `@Id` annotations are not evaluated for this purpose

##### Validation

**Example 1: Without @Id, any input can be used (getTradeByTicker works)**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WSwU_CMBTG_5WXnjRZQvS4k3ghGCNGSDywHcr2YJWunW1HJMv-d18Zw805Ermsr_1e3_f9SsVskmHOWchynaKEleEpLss85-YIVaSAfs7vmXkaNqe0AqEyNMJZWDoj1O6sE8kezVm1OhVDYR2pzqTxEd39F55je-LXf95q0RxEgo1sWoj2al2g4U5oBTt0nXgC7c1t2Au8jsdaHo9NnptOtraZprOAfZZojoRxMoF34TJdOnggUHTHSRQAV0fgBy4k30ifoCBFwhVsEEqLlJc6e-RsR741Ou9ZDcDqgTvfg1LsBLVEaitUCtWvgFADtxfqwwe7hp2CrmOKWnBDlUNjWVjVAbOu3NByXbELOC8nGAPi1G3QFppei47XVcTal49YCFTdRSzw38bMaW86fX2OWB3AUH0_VM8WixmpvU9MMj33nFm45dJiwBKtUuH9cfl2tuGNx3Uw7r2F27c-7vwHV-Ne0p-SDP3PT0zqrwITh-mT1aplNaBybWIDbIDmWounZh1P9vOUhaqUkh7b6A_y0ZT1NwLAEoUwBAAA=)

```json
{
  "schema": "model TradeSummary {\n    traderId: TraderId inherits String\n    ticker: TradeTicker inherits String\n}\nmodel Trade {\n    traderId: TraderId\n    traderName: TraderName inherits String\n}\nservice TradeApi {\n    operation getTradeSummaries(): TradeSummary[]\n    operation getTradeByTicker(TradeTicker): Trade\n}",
  "query": "// Without @Id on Trade, any available input can be used\n// TradeTicker is available from TradeSummary, so getTradeByTicker is eligible\nfind { TradeSummary[] } as {\n    ticker: TradeTicker\n    traderName: TraderName\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getTradeSummaries",
      "response": "[{\"traderId\": \"t1\", \"ticker\": \"AAPL\"}, {\"traderId\": \"t2\", \"ticker\": \"GOOG\"}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getTradeByTicker",
      "response": "{\"traderId\": \"t1\", \"traderName\": \"Alice\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"ticker\": \"AAPL\", \"traderName\": \"Alice\"}, {\"ticker\": \"GOOG\", \"traderName\": \"Alice\"}]"
}
```

**Example 2: With @Id on Trade, only TraderId-accepting operations are used**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5VSwW7CMAz9FSsnkCqh7Vhp0tiFMU1jGkg7UA4hMZDRpl2STqCq_z6npVDoQFovtZP37OfnFMyKDSachSxJJcYwM1ziNE8SbvZQRBroc_7MjGVY31IESm_QKGdh6ozS6wNOiS2aA2pWJV1gGelWp6bFI9Xstmm3f-MJNjc-_rOyRfOjBNawYaaa8mmGhjuValija42o0Pb64dnQ88U1ytO-nqnXmq8hX-eMZa8Z5wQuWcC-czR78n0wgM_x7LmygJgVIqAo3p_qWeBCYOZoztMOBNewRMgtklFU5VInKAu4E3EuUULP8S3a9moC0Kk7VutflPA7tlXtM6rv-wBug14ucVZKSyguDIQSuD0-nu6juLVW8ma-IHcybihzaCwLizJg1uVLCucFO5ri4eRfZ6PENmgzcs1fz4uINS8rYiFQdhexwP9rMdXZcPj-GrEygC76voseTSYjQnudKDbpWGe5Y-GKxxYDJlItldfH44-DDC98UQbXtXu_z2VfV32yqlYe04MnMf_TsiD0LkPhUL7YVDc-dRy51bE2q2PLLYp3zDoutjRuqPM4pkWb9It01Gn5C4qr-52QBAAA=)

```json
{
  "schema": "model TradeSummary {\n    traderId: TraderId inherits String\n    ticker: TradeTicker inherits String\n}\nmodel Trade {\n    @Id traderId: TraderId\n    traderName: TraderName inherits String\n}\nservice TradeApi {\n    operation getTradeSummaries(): TradeSummary[]\n    operation getTradeByTicker(TradeTicker): Trade\n    operation getTradeById(TraderId): Trade\n}",
  "query": "// WITH @Id on Trade, only operations accepting TraderId can be used\n// getTradeByTicker is excluded (takes TradeTicker, not TraderId)\n// getTradeById is used (takes TraderId = the @Id)\nfind { TradeSummary[] } as {\n    ticker: TradeTicker\n    traderName: TraderName\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getTradeSummaries",
      "response": "[{\"traderId\": \"t1\", \"ticker\": \"AAPL\"}, {\"traderId\": \"t2\", \"ticker\": \"GOOG\"}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getTradeById",
      "response": "{\"traderId\": \"t1\", \"traderName\": \"Alice\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"ticker\": \"AAPL\", \"traderName\": \"Alice\"}, {\"ticker\": \"GOOG\", \"traderName\": \"Alice\"}]"
}
```

---

## 5. Expressions

**Impact: HIGH**

Computing derived values in projections. Covers arithmetic and string expressions, `when` conditional blocks, reusable expression types defined in the schema, scoping rules for type-based vs variable-based access, date formatting with `@Format`, and using expression types as `::` traversal nodes. Apply these after mastering projections and discovery.

---

### 5.1 Use inline expressions to compute values in projections

**Impact: MEDIUM (enables derived fields and calculations without schema changes)**

Inline expressions compute values directly within a projection. They support arithmetic, string operations, and function calls.

##### Arithmetic expressions

```taxi
find { Flight[] } as {
    flightNumber: FlightNumber
    totalSeats: TotalSeats
    soldSeats: SoldSeats

    // Compute using type references (preferred when unambiguous)
    utilization: Decimal = (SoldSeats / TotalSeats) * 100

    // Compute using field references (with 'this') when types are ambiguous
    remainingSeats: Int = this.totalSeats - this.soldSeats
}[]
```

##### When to use `this.fieldName` vs type references

- **Type references** (e.g., `SoldSeats / TotalSeats`) — preferred when the type is unambiguous
- **`this.fieldName`** — required when the same type appears on multiple fields

```taxi
model Transfer {
    sourceAmount: Money
    targetAmount: Money
}

// Money is ambiguous — two fields have the same type
find { Transfer[] } as {
    difference: Decimal = this.sourceAmount - this.targetAmount
}[]
```

##### String expressions

```taxi
find { Person[] } as {
    fullName: String = concat(FirstName, ' ', LastName)
    upperName: String = FirstName.upperCase()
}[]
```

##### Expressions that trigger data discovery

Expressions can reference types not on the source object — TaxiQL discovers them:

```taxi
model Order {
    basePrice: Price
    customerId: CustomerId
}

// DiscountRate is not on Order — TaxiQL calls services to find it
find { Order[] } as {
    finalPrice: Decimal = Price * (1 - DiscountRate)
}[]
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42RQWuEMBCF_StDTrtgxRakIGyhH5f2UAp73HhI47imG6ONsXQR_3sTo-u23aVQczCZyTt55p0OGl5gyYCgqjKk5EVXWcsN6agCotoqM4VeTQagqwl73UHXRgu1dZc4M7ittD4h96PuZK3aC97Xsr85_4BclExS1UMAr52ovQXZig9UHoBMj29SsiIbH3JfN7IRuK1ridSCIw4KuW6FccHx2Ysxjkgf3NKvDbLdj3yJ7Js8DqNz-jumrPsLIAqv4_MATJaV-lVANMWRfioaCLw-tX5RlQuVGTPHgcJcSIN6sZjGsCQXN_MMVquZawnkd920RQCotIOk6wNo0ft6u5kEAD-r5Aazp6ZSdiLe-G6ip-DYIYZ4PYemLnx-MmLODx25pB3KEB1tOVn-4O4_6jvP_QNUpeD6Y3z3mEGiWilt-7p6s037Y_8FoHVkJwkDAAA=)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Decimal\n  quantity: Quantity inherits Int\n}",
  "query": "given {\n    Product[] = [\n        { name: \"Apple\", price: 1.50, quantity: 10 },\n        { name: \"Banana\", price: 0.75, quantity: 20 }\n    ]\n}\nfind { Product[] } as {\n    name: ProductName\n    price: Price\n    quantity: Quantity\n    totalValue: Decimal = Price * Quantity\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"name\": \"Apple\",\n      \"price\": 1.5,\n      \"quantity\": 10,\n      \"totalValue\": 15.0\n   },\n   {\n      \"name\": \"Banana\",\n      \"price\": 0.75,\n      \"quantity\": 20,\n      \"totalValue\": 15.0\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQQUvEMBCF_ctDTntIfoGCNxFcRIQeNz3EdroN25k1SUUJ_u9ON8uyCA1pEt6b974kDO1Ak8EKJ9fRCG_kg2NImgF664N9NRNVsBQGsDyQt5HiGLssp1U3miJ7MXuqBRV-zeR_Jek0v2mLgC2wbuAB6vyynvQbrvHgBtaobrI0PjrSCIvaNxmm_6bjZOOw2rKrkaJae8udiK9NFjCh1OvncczuTCI1W8etiffXL1GyF9agQn8vW-tGgM_Gyz1KXqzSojDE-V3GuoFIP2dqI3UHiZQvSeipMGgsuRoLP1yAL4oNet8uZLChZoPmtU-Ipv184bBikUs97z6kRL4uf7mPZb8LAgAA)

```json
{
  "schema": "model Person {\n  firstName: FirstName inherits String\n  lastName: LastName inherits String\n}",
  "query": "given {\n    Person[] = [\n        { firstName: \"John\", lastName: \"Doe\" },\n        { firstName: \"Jane\", lastName: \"Smith\" }\n    ]\n}\nfind { Person[] } as {\n    fullName: String = concat(FirstName, \" \", LastName)\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"fullName\": \"John Doe\"\n   },\n   {\n      \"fullName\": \"Jane Smith\"\n   }\n]"
}
```

---

### 5.2 Format date and time values in projections

**Impact: MEDIUM (controls how dates appear in query output without changing underlying types)**

The `@Format` annotation controls how date, time, and instant values are serialized in query output. It can be applied inline on projection fields, on named type definitions in the schema, or both.

##### Inline `@Format` on projection fields

Apply `@Format` directly to a field in a projection to control its output format:

```taxi
find { Event[] } as {
    name: EventName
    @Format("dd/MM/yyyy HH:mm")
    displayTime: OccurredAt        // Formatted as "15/06/2024 10:30"

    @Format("yyyy-MM-dd'T'HH:mm:ssXXX")
    isoTime: OccurredAt            // Formatted as "2024-06-15T10:30:00Z"
}[]
```

The same source type (`OccurredAt`) can appear multiple times with different formats.

##### `@Format` on schema-defined types

Define a named formatted type in the schema, then cast to it using the `(FormattedType) BaseType` syntax:

```taxi
@Format("yyyy-MM-dd'T'HH:mm:ssXXX")
type FormattedInstant inherits Instant

given { timestamp: Instant = parseDate('2023-12-25T14:30:00Z') }
find {
    // Cast to FormattedInstant to apply its @Format
    asFormatted: (FormattedInstant) Instant
}
```

##### Inline format overrides schema-defined format

When a field has both an inline `@Format` and its type has a schema-defined `@Format`, the inline annotation wins:

```taxi
@Format("yyyy-MM-dd")
type DisplayDate inherits Date

given { d: Date = parseDate('2024-06-15') }
find {
    // Uses the schema format: "15/06/2024" → wait, type uses yyyy-MM-dd
    schemaFormatted: (DisplayDate) Date

    // Inline overrides schema format
    @Format("dd/MM/yyyy")
    inlineFormatted: (DisplayDate) Date   // Uses "dd/MM/yyyy" instead
}
```

##### Format pattern reference

| Pattern | Meaning | Example |
|---------|---------|---------|
| `yyyy` | 4-digit year | `2024` |
| `MM` | 2-digit month | `06` |
| `dd` | 2-digit day | `15` |
| `HH` | 24-hour hour | `14` |
| `hh` | 12-hour hour | `02` |
| `mm` | Minutes | `30` |
| `ss` | Seconds | `00` |
| `a` | AM/PM | `pm` |
| `z` | Timezone name | `UTC` |
| `XXX` | ISO timezone offset | `+00:00` or `Z` |

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WRX0_CMBTFv8pNX4Bky8aQlyYmGo0Bk73NhGh9KPTKqqybbUFx4bt7F_6ogBH71N7ec077uzVzkxwLyThjAXudo13S9uKmtIX0bcGWtMI0DZVqZa3BgBcFd240GgnWEcYvK4R1q0c1NM5L40GbHK32DjYFYYSZ6gUaqMHrAqlYVHx7C-dQSevwWnpst5I46YXdJEz6WfeM92Iex_etzkqYJ20U1MIArRNf17RqV2bbSNhlBs2TfjopFaVp1PhBnpMPSPjYuViUSo5neMRq6_SmfX65Q5ERGA7tfTSdfdWJ-eUCrdVKm-k_EggaTZTYygI9Wsd4vQqY8_MxbR8eA4bvFU5IeutKQzNfwxXsOzLBOFWODUWwYNN_AGcj6kfdJGqkECckgqqAu-zqS3dI7JS0X1D8kUk0WPN3OXkZKsbNfDYjNLZ8JgDr4-oTFwyrTQkDAAA=)

```json
{
  "schema": "",
  "query": "@Format(\"yyyy-MM-dd'T'HH:mm:ssXXX\")\ntype FormattedInstant inherits Instant\n\ngiven { timestamp: Instant = parseDate('2023-12-25T14:30:00Z')}\nfind {\n    @Format(\"yyyy-MM-dd'T'HH:mm:ssXXX\")\n    isoTimestamp : Instant,\n\n    @Format(\"dd/MM/yyyy hh:mm a z\")\n    readableTimestamp : Instant\n\n    withAFormattedType: (FormattedInstant) Instant\n\n    @Format(\"dd/MM/yyyy hh:mm a z\")\n    overridingFormattedType: (FormattedInstant) Instant\n}\n",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n   \"isoTimestamp\": \"2023-12-25T14:30:00Z\",\n   \"readableTimestamp\": \"25/12/2023 02:30 pm UTC\",\n   \"withAFormattedType\": \"2023-12-25T14:30:00Z\",\n   \"overridingFormattedType\": \"25/12/2023 02:30 pm UTC\"\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/51SwW7bMAz9FYGXtoANy9laYDp1KDY0w7IBaw7Bohw0i220-ZInycUCw_9eyfKSrWl7qE8Uyff4HukeXLXFRgCDxkisyYd71J70XBOiRYMsJb6EkCi9Rau8IzfeKn0XW0xVddaifO8Z-bqPD51z7bzQnuuBa4f2XlWYCG-mxzjItGiFV0aTO_Rj2Z2eTZPXmwCGDH53aHdB5a3SkvR_a2QgwiWSI70pefnR2Eb4Uw5SFotFsQsfub5mTcPhLLVI5dpa7JYqwg8uHuMjMl8scilPlicjA3NutVrteZQzxxzDehPUt8IGRR6tA9YPGTjf_Qjhuoe99yg5-NtvIKAsutaEvYX0uucQ_XFghMNn0elqyyEL8eEEqTajs7c5vcjL82VJ2RvKKP3OYcjIfwxXtXH4AkE5y2m5pO8CeiKINrDamrluOw_sVtQOM6iMlirKF_W3SW30tRk2oftPi5VH-ckZ_aKFfw6QiuV5QS-KKISMHlLbtN9X2jwaQsuinKUho8_nhjy9ivBfV7_mEpju6joc2JqfwWx6Dg-4tldjVgMAAA==)

```json
{
  "schema": "model Event {\n  name: EventName inherits String\n  occurredAt: OccurredAt inherits Instant\n}\nservice EventService {\n  operation getEvents(): Event[]\n}",
  "query": "find { Event[] } as {\n    name: EventName\n    @Format(\"dd/MM/yyyy HH:mm\")\n    displayTime: OccurredAt\n    @Format(\"yyyy-MM-dd'T'HH:mm:ssXXX\")\n    isoTime: OccurredAt\n}[]",
  "parameters": {},
  "stubs": [
    {"operationName": "getEvents", "response": "[{\"name\": \"Launch\", \"occurredAt\": \"2024-06-15T10:30:00Z\"}, {\"name\": \"Close\", \"occurredAt\": \"2024-12-01T09:00:00Z\"}]"}
  ],
  "expectedJson": "[{\"name\": \"Launch\", \"displayTime\": \"15/06/2024 10:30\", \"isoTime\": \"2024-06-15T10:30:00Z\"}, {\"name\": \"Close\", \"displayTime\": \"01/12/2024 09:00\", \"isoTime\": \"2024-12-01T09:00:00Z\"}]"
}
```

---

### 5.3 Use type-based access in expressions and lambdas, not field names

**Impact: HIGH (prevents breakage when schemas evolve, and aligns with Taxi's semantic-first design)**

In TaxiQL, data can be accessed by **type** or by **field name**. Type-based access resolves a value by finding a field of that type on the object being evaluated. It stays correct even if the field is renamed, moved, or the model is restructured.

This applies everywhere: projection expressions, lambda parameters in `.filter()`, `.map()`, `.single()`, and any other collection operation.

There are two dimensions to this rule:

1. **Lambda parameters** should be declared as a type (e.g., `(OrderStatus)`) rather than a named variable with field access (e.g., `(order:Order) -> order.status`).
2. **Projection expressions** should reference types directly (e.g., `SoldSeats / TotalSeats`) rather than using `this.fieldName` (e.g., `this.soldSeats / this.totalSeats`). Only use `this.fieldName` when the type is ambiguous — i.e., the same type appears on multiple fields.

**Incorrect (field-based access in lambda — coupled to field name):**

```taxi
find {
   Order[].filter((order:Order) -> order.status == 'pending')
}
```

**Correct (type-based access in lambda — decoupled, resilient to renames):**

```taxi
find {
   Order[].filter((OrderStatus) -> OrderStatus == 'pending')
}
```

**Incorrect (field-based access in projection — unnecessarily coupled):**

```taxi
find { Flight[] } as {
   flightNumber : FlightNumber
   utilization : Decimal = (this.soldSeats / this.totalSeats) * 100
}[]
```

**Correct (type-based access in projection — preferred when types are unambiguous):**

```taxi
find { Flight[] } as {
   flightNumber : FlightNumber
   utilization : Decimal = (SoldSeats / TotalSeats) * 100
}[]
```

**When `this.fieldName` IS appropriate (the same type appears on multiple fields):**

```taxi
model Transfer {
   sourceAmount: Money
   targetAmount: Money
}

// Money is ambiguous here — two fields have the same type.
// Use this.fieldName to disambiguate.
find { Transfer[] } as {
   difference: Decimal = this.sourceAmount - this.targetAmount
}[]
```

The same principle extends to `::` deep traversal inside lambdas. When the type you need is nested within the lambda parameter, use `::` to traverse into it rather than chaining field names:

**Incorrect (chained field names inside lambda):**

```taxi
find {
   Customer[].filter((c:Customer) -> c.account.status == 'active')
}
```

**Correct (type traversal inside lambda):**

```taxi
find {
   Customer[].filter((Customer) -> Customer::AccountStatus == 'active')
}
```

##### Examples
[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41Qy07DMBD8FWsvaaXAB1gKEuJEJbj0GOdgnG1rajvFD0QV5d/ZxCVAyQGf9jGemZ0egjqglcDBdi0a9pBC7Cx61gvHmJMW+Tx7po5pd0CvY2Db6LXbj6gQZUyBs3uluuTidmr/Agco4S2hP5PYXr+jyxps5q8bVrE6z8bXX/SLjbb2XJSzUCFVJIJiKJfAUh1/YpV0Co3Bdhn+JP0idYY2ZFu4nXYt/bh2e7vTJqJfrX4dvmY3d1dRVNXMu845nKQnefocgPdDCSGmFyrrpgT8OKGK2G5C5yipnEf/5VzA6FsAp2qKRUD5vctX5G0WFDBtL6cv0owJ/JNFuAZGsxTxYwvcJWPoFt+9kuPcDp/+21dKUgIAAA==)

```json
{
   "schema": "model Customer {\n  name: CustomerName inherits String\n  status: AccountStatus inherits String\n}",
   "query": "given {\n    Customer[] = [\n        { name: 'Jimmy', status: 'active'},\n        { name: 'Jack', status: 'cancelled'},\n        { name: 'Mary', status: 'active'}\n    ]\n}\nfind { \n    Customer[].filter((AccountStatus) -> AccountStatus == 'active')\n}",
   "parameters": {},
   "stubs": [],
   "expectedJson": "[\n   {\n      \"name\": \"Jimmy\",\n      \"status\": \"active\"\n   },\n   {\n      \"name\": \"Mary\",\n      \"status\": \"active\"\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/32RwU7jMBCGX2XkU4uibulCWSztYSWEVA6VdsuJpgc3GVqDYwd7gliivDvjOFBWKitFyYxn5s//eVoRij1WSkhRuRINXBu92xO0uQWwTbVFL4ezZZ+Btnv0mgKsyGu7i30BFQWZZgDIkTISbuNnFSuHkYWl1BOcKSWs+H28o8stP7kN6J91gYODX7VOP3E1ekXaWdghpVoYjWWK1hseFpl4atD/ZS5d1c4TkHrRk0Cl0duJd40tc3uvbQktvI9BByq8UxxjT5WGtNGv/e8lXGGhK2XgJ/Sao9GB6dunKxjDCZxOp9lszN7WG3ZXK68qJPRByLbLRKBmy+G6FR9wS25g/wdEHvMYasfXwufrZKeFXCSzuZAc/74+zUXGQb+VeBY7+qXE5IxdxCLbjOnscgpdlx2XWt59/5/U/F+p8x9RKrd8/WwUi71b2LohIe+VCZiJwtlSRy5l/gwUEXjTbbj7pcaCsLwJzn6gDZuAo4CH2qd9xIaL2eS8Lw5UX6gkti9VLueT+UWSYSIR96OKx0UppG2M4fV598COU9q9AQstOwlGAwAA)
```json
{
   "schema": "model Flight {\n  number: FlightNumber inherits String\n  seats: {\n    total: TotalSeats inherits Int\n    sold: SoldSeats inherits Int\n  }\n}\n\nservice FlightApi {\n  operation getFlights():Flight[]\n}",
   "query": "import taxi.stdlib.round\nfind { Flight[] } as {\n    number: FlightNumber\n    utilization: Decimal = round((SoldSeats / TotalSeats) * 100,2)\n}[]",
   "parameters": {},
   "stubs": [
      {
         "operationName": "getFlights",
         "response": "[\n    { \"number\": \"QF1\", \"seats\": { \"total\": 400, \"sold\": 290 }},\n    { \"number\": \"NZ3\", \"seats\": { \"total\": 600, \"sold\": 580 }}\n]\n"
      }
   ],
   "expectedJson": "[\n   {\n      \"number\": \"QF1\",\n      \"utilization\": 72.5\n   },\n   {\n      \"number\": \"NZ3\",\n      \"utilization\": 96.67\n   }\n]"
}

```

---

### 5.4 Understanding expression and projection scoping rules

**Impact: HIGH (prevents common errors from incorrect scope assumptions)**

TaxiQL has two ways to reference values in expressions: **type-based** and **variable-based**. Understanding which is available and when is critical for writing correct expressions.

---

##### Rule 1: Type-based access works everywhere

In any expression — whether inside an expression type definition or an inline projection expression — you can reference values by their **type name**. The query engine resolves the value by finding something in scope with that type.

```taxi
// Works in all scopes
find { Order[] } as {
    orderId: OrderId
    total: Decimal = BasePrice * Quantity   // types resolved from Order in scope
}[]
```

**This is the preferred approach** because it doesn't depend on how the projection is named.

---

##### Rule 2: `this.field` only works with a named scope variable

`this.field` and named variable access (`variable.field`) require the source object to be explicitly named in the projection scope. Without a named variable, `this` refers to the anonymous output type, which doesn't have the source fields.

**Incorrect (anonymous scope — `this.field` fails):**

```taxi
find { Order[] } as {
    total: Decimal = this.basePrice * this.quantity   // ERROR: basePrice not on anonymous type
}[]
```

**Correct (named scope variable):**

```taxi
find { Order[] } as (o: Order) -> {
    total: Decimal = o.basePrice * o.quantity   // Works: o refers to Order
}[]
```

**Also correct (type-based — no named variable needed):**

```taxi
find { Order[] } as {
    total: Decimal = BasePrice * Quantity   // Works in any scope
}[]
```

---

##### Rule 3: Expression types should declare explicit inputs

When defining expression types in the schema, declare explicit inputs. This improves documentation, makes the expression self-contained, and creates a clear scope boundary for evaluation.

**Without explicit inputs (implicit discovery):**

```taxi
type RemainingSeats inherits Int = TotalSeats - SoldSeats
```

**Preferred: with explicit inputs:**

```taxi
// Inputs are explicit — clear what data the expression needs
type RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold
```

Both forms work. The explicit form is clearer about dependencies and avoids ambiguity when the same type appears multiple times in a complex model.

---

##### Rule 4: Named scopes enable access to outer context in nested projections

When an inner projection needs data from the outer scope (e.g., a parent field), use named scope variables at the outer level.

```taxi
find { Customer[] } as (customer: Customer) -> {
    name: CustomerName
    orders: Order[] as (order: Order) -> {
        orderId: OrderId
        amount: Amount
        // Inner scope can access outer scope variable
        customerTier: CustomerTier = customer.tier
    }[]
}[]
```

Without the named `customer` variable at the outer level, the inner scope cannot reference the parent customer's fields.

---

##### Rule 5: Type resolution in expression types uses discovery

Expression types that reference types not on the source model will trigger data discovery. The query engine will call services to resolve the missing types:

```taxi
type TaxAmount inherits Decimal = (p: Price, r: TaxRate) -> p * r
type PriceWithTax inherits Decimal = (p: Price, t: TaxAmount) -> p + t
```

If `TaxRate` isn't on the source model, the engine will look for a service that can provide it.

---

##### Validation

**Example 1: Type-based access in anonymous scope**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4WQy07DMBBFf2XkFaBAX7CxxAaxCQteZdd04drT1jSxU9sBoij_zrhNUyQq2M3jXs250zAv11gIxllhFebw5BQ6aDIDYGOZKr6fpQq0WaPTwcM0OG1WUbMQHp-dlsjh7lAedfcodSHyKNxWwgQdag4vXXWUpSZkps2MR_cR_bt7067Zo5ToRNDWwArDbu3Pzjuw2ZzMLGHbCl1NOZbaKGgOO2hB-NN54mwwgLe6xMuYQ4GQEr2HT-s2nvBAmBq8pONRGmwQOT9kgtsfgS_6UIQymxNMKZwoMBAn402bMB-qBZWzhvVRHklAuH0gcjn0paU30HjWZKwDzhiHXXM5ylhCZf_0uBnfXA3j8PDgOLtuEzjlH5_wj4a__ZM2ZkC5tqkpq8D4UuQeEyatUTqyi_y1Q42h5u2c1F8lyoDqwVvzD__uk_vbdPxv1l47idKI5YOQm1Qxbqo8p087-06H9237DY83P2zQAgAA=)

```json
{
  "schema": "model Order {\n  orderId: OrderId inherits String\n  basePrice: BasePrice inherits Decimal\n  quantity: Quantity inherits Int\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}",
  "query": "find { Order[] } as {\n  orderId: OrderId\n  // Type-based access works in any scope\n  total: Decimal = BasePrice * Quantity\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getOrders",
      "response": "[{\"orderId\": \"ord-1\", \"basePrice\": 25.0, \"quantity\": 4}, {\"orderId\": \"ord-2\", \"basePrice\": 10.0, \"quantity\": 3}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"orderId\": \"ord-1\", \"total\": 100.0}, {\"orderId\": \"ord-2\", \"total\": 30.0}]"
}
```

**Example 2: Named scope variable for field access**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4VRy27bMBD8lQVPSSE7z14IpIegF_XQNg-kB8uHNbmJ2MikQlJJDEH_nqUlK0Ljtrd9zO7OzLYiqJLWKKRYO00V_PCaPLSFBXApzLXsa7kGY0vyJga4id7Yh4RZYaCf3iiScLkL33FfSZk1Vgn41KCNJm4kXA3ROyy3sbBdYQP55zS_vXczJD2VmjxG4yw8UNy2w8HhQGyx5GGRiaeG_IZ13BuroMPtoBMMcOAG8CHMvuxXl2pHR_DLxBIsrklDUHw2g1iaMIcX5x8DRAeoFAWOSoLgGs8Mn9EbXFWUNkQXsbozeIde7uTDBbj5aBR84mznxnD0dlPTLCE0YBVcf2y6LgGm-y4n267GXd1iyT7U6Jl-ZIuEbLtMhNisOFy0YnTxOwPYqdFLnvIUascf4PKiLcTgTiEkbJPZSSEyDkcZqXP6eX6ciqMarp13GeybP90zf3L8cf6sSxpIlS63dROFvGdHKBPKWW0Sd6yuB6pJ1LJbMvq1JhVJfwvO_of_5D89g4HC1Oix8W8pf6w6-8umVN9qChHVY66FtE1V8Zu8-82s-7R7A315Nh-IAwAA=)

```json
{
  "schema": "model Flight {\n  flightNumber: FlightNumber inherits String\n  totalSeats: TotalSeats inherits Int\n  soldSeats: SoldSeats inherits Int\n}\nservice FlightService {\n  operation getFlights(): Flight[]\n}",
  "query": "find { Flight[] } as (f: Flight) -> {\n  number: FlightNumber\n  remaining: Int = f.totalSeats - f.soldSeats\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFlights",
      "response": "[{\"flightNumber\": \"QF1\", \"totalSeats\": 400, \"soldSeats\": 290}, {\"flightNumber\": \"NZ3\", \"totalSeats\": 600, \"soldSeats\": 580}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"number\": \"QF1\", \"remaining\": 110}, {\"number\": \"NZ3\", \"remaining\": 20}]"
}
```

**Example 3: Expression type with explicit input declarations**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41SwUrDQBD9lWFPFiK2iggBBUWEehBs9dT0sE2m7epmN-5ORA35d2eTNK21oLeZeS9v3rxNJXy6xlyKWOQ2Qw13Wq3WBFViAJZN_VDmC3Rxh7QdKLNGp8jDlJwyq8AmS1JPUZKP4amvt8yxoUDzVmcda7op90h1YuizQJiwMWVY_jcJLuGoWbi7K2rEd3QHcHzV-oLjBuuEn0lp9SVJWTORhFvlW0xVzmxW35OK4Ne6Rj3Q4KQFE-PRvasUu6ymXdeEaQt0zUZYIbW4PxpsYp3N-WwRibcS3Sc_xlKZDKoehBqk_-tRAuo2kcV76bH8bM4LCulkjoTOi7iqI-GpXHA5q0Rv8IEJbGFrkz9z6AvL5_F8ViVi10QiYkjE490oEREX298gAKfDYRj2jx5mo_NhHcFBlZvri0MqZwdUeFaHgzBd27EpShLxUmqPkUityVQ4ROpJZztcOK_nzP4oMCXM7r01f9_Spxnm_3D9g9_a8yTT13EmYlNqzfE7-wIG2rb-BpaFCAp-AwAA=)

```json
{
  "schema": "model Flight {\n  flightNumber: FlightNumber inherits String\n  totalSeats: TotalSeats inherits Int\n  soldSeats: SoldSeats inherits Int\n}\ntype RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold\ntype UtilizationRate inherits Decimal = (sold: SoldSeats, total: TotalSeats) -> sold / total\nservice FlightService {\n  operation getFlights(): Flight[]\n}",
  "query": "find { Flight[] } as {\n  flightNumber: FlightNumber\n  remaining: RemainingSeats\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFlights",
      "response": "[{\"flightNumber\": \"QF1\", \"totalSeats\": 200, \"soldSeats\": 150}, {\"flightNumber\": \"BA7\", \"totalSeats\": 300, \"soldSeats\": 300}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"flightNumber\": \"QF1\", \"remaining\": 50}, {\"flightNumber\": \"BA7\", \"remaining\": 0}]"
}
```

**Example 4: Nested scope — inner projection accesses outer scope variable**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/7VTy27bMBD8lQVPNqAkjo8CWiBJ0cA51EDdm6UDTTExW4pUScoQIejfu3xYki05cIG2J3GXy9mdmVWDDd2zkuAUl6pgAj3VxqqSadRkEiEao1WRdjergp6cM82tQRurufzmKiUp3V3zBaKpKsuZ7qu-QTSuajMZRlnr4jiHckc3xDocprCnZ3U3pFS1tCl68N_-7SdGeUmEb2mYPnDKupebGIf2FdPEcqXQG7PHCjOb940-hA2KH_NxhL82n5U-Pp_1A88jaY-IE_yzZvo32PPKZYGaQUPUIqL37Ei7Lxs5uoehOTak24ROM-vaN2yfj6ledbRGSHvKXej27kBuCTYaCgogQikzBgxDqrZd9kA0JzvBwhM6GAwkWocZTnbmQ1d0a-P4rddrm4NiFdHAFOANTps2wcbWOzhuG9yZ4KQATYduwkPNTKXAQ7jZNhnuFyrDKYL4PsMJfJ2qIfMgwN-Q9HP45LMS8KBN0BTE8hziUe3OATZcHNy5dWQY3auVrGqL01ciDEswVbLgjgQRX-PAjl3eJpMER7s2JhrtDN0huIlELwkQjHa5-8XidhGojkCW14IsPchfks2h-lfFqGXFi1EyMrnKmgTFaY3LvifAGdEhneF2jn2_JMYZ6asA8wB51cr8J2rdRv47cv2Se-eNJfTHqsCprIWAX1ir7-BtCNs_v7wmpqUGAAA=)

```json
{
  "schema": "model Customer {\n  customerId: CustomerId inherits String\n  name: CustomerName inherits String\n  tier: CustomerTier inherits String\n}\nmodel Order {\n  orderId: OrderId inherits String\n  customerId: CustomerId\n  amount: Amount inherits Decimal\n}\nservice CustomerService {\n  operation getCustomers(): Customer[]\n}\nservice OrderService {\n  operation getOrdersForCustomer(CustomerId): Order[]\n}",
  "query": "find { Customer[] } as (customer: Customer) -> {\n  name: CustomerName\n  tier: CustomerTier\n  orders: Order[] as (order: Order) -> {\n    orderId: OrderId\n    amount: Amount\n    // inner scope accessing outer scope variable\n    customerTierForOrder: CustomerTier = customer.tier\n  }[]\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getCustomers",
      "response": "[{\"customerId\": \"c1\", \"name\": \"Alice\", \"tier\": \"Gold\"}, {\"customerId\": \"c2\", \"name\": \"Bob\", \"tier\": \"Silver\"}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getOrdersForCustomer",
      "response": "[{\"orderId\": \"ord-1\", \"customerId\": \"c1\", \"amount\": 100.0}, {\"orderId\": \"ord-2\", \"customerId\": \"c1\", \"amount\": 200.0}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"name\": \"Alice\", \"tier\": \"Gold\", \"orders\": [{\"orderId\": \"ord-1\", \"amount\": 100.0, \"customerTierForOrder\": \"Gold\"}, {\"orderId\": \"ord-2\", \"amount\": 200.0, \"customerTierForOrder\": \"Gold\"}]}, {\"name\": \"Bob\", \"tier\": \"Silver\", \"orders\": [{\"orderId\": \"ord-1\", \"amount\": 100.0, \"customerTierForOrder\": \"Silver\"}, {\"orderId\": \"ord-2\", \"amount\": 200.0, \"customerTierForOrder\": \"Silver\"}]}]"
}
```

**Example 5: Composing expression types with explicit inputs**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5VRy07DMBD8lZVPLYSqrThFAgkJCZUDQi1SD0kOJnFbQ2Ibe4NaRfl31qnpiyLEyd7x7O7MuGEuX4mKs5hVuhAlPFtd1DlCkyoAxSsRf0NPVIBUK2ElOpihlWrpScbKvGPRsX-_F7mseOkJyNdTjkR52V7OkNpU4cYIz7irdK3wBwduoGfClgjsblgfrm7BwAXYMKJjzCWuiPDHFIz3C8OcS8BUOWE_vZlgfBbKLhJthOUotYKlwEBwvf4upSQjNyxiH7WwG0p1IVUBzf4VWuDul3RP0wzhHajsEI28jI980soko6WGWxqDwjoWN23EHNavdE0atpPt95CsA_HUZ4UzmmzTQ9KkzAtLWQwpm8uCmCmL6N4p8_BoOBwMPRI-1mPDwaiN4Kj3gZ_pHZ_tHbdevchXeqJMjSxe8NKJiOVaFdKr5uU0SPR2sjYj9tqIHEXx6LT6v-5tHUofaAd45N82fH19Omrskc6VQ56_TwoWq7os6YusfiPd27L9AgLefuZ_AwAA=)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Decimal\n  taxRate: TaxRate inherits Decimal\n}\ntype TaxAmount inherits Decimal = (p: Price, r: TaxRate) -> p * r\ntype PriceWithTax inherits Decimal = (p: Price, t: TaxAmount) -> p + t\nservice ProductService {\n  operation getProducts(): Product[]\n}",
  "query": "find { Product[] } as {\n  name: ProductName\n  price: Price\n  tax: TaxAmount\n  total: PriceWithTax\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getProducts",
      "response": "[{\"name\": \"Widget\", \"price\": 100.0, \"taxRate\": 0.1}, {\"name\": \"Gadget\", \"price\": 200.0, \"taxRate\": 0.2}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"name\": \"Widget\", \"price\": 100.0, \"tax\": 10.0, \"total\": 110.0}, {\"name\": \"Gadget\", \"price\": 200.0, \"tax\": 40.0, \"total\": 240.0}]"
}
```

##### Problems

**`this.field` in anonymous projection scope**

Using `this.field` in an anonymous projection (without a named scope variable) results in a compile error. `this` refers to the anonymous output type, which does not have the source fields.

Expected: `this.basePrice * this.quantity` to resolve fields from the source `Order` in scope.

Actual: Error — `Field basePrice does not exist on type AnonymousType...`

The fix is to either:
- Use type-based access (`BasePrice * Quantity`)
- Declare a named scope variable (`as (o: Order) -> { total: Decimal = o.basePrice * o.quantity }`)

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22QT0-DQBCFv8pkT21CDa0fURI9NqmHJoongcMWhrIKu7i7GA3hvztbKKjhNh_vzLzPtMykBVacBaxSGZawLcWxsNDGEiA_xfumOqAOhk6fgZAFamENhFYLeXRqqywvQ-TWBPAyxpNyJ62TGVVmgyo8h_9EXSwN6k-R4nA0HLKTK1Wj5lYoCUe0fd8slmd_UULzzGMfDepvosqFzKAdm9ABN7DIz_IlrB76tXIG09U1fUdIYgycObiH_GIihRWlIxFdjhK6XXPNK7SoDQvazmPGNgcKo5aN3vckIHcTAY1pNLUicqpHbcx-fz9mAcTsabuOmUfBZMA1rnzfFScbVNvc-Z0Hs1v2r5dzW25mtlzf-p0DwrRQO1k3lgU5Lw16LFUyEw6El8-DbUeYdAmpv2pMLWaPRsmBRc5RjJ919fV6MCznrP6RbnpTxvL0fZexQDZlSU_X6o3O9mn3Ax3KoQDYAgAA=)

```json
{
  "schema": "model Order {\n  orderId: OrderId inherits String\n  basePrice: BasePrice inherits Decimal\n  quantity: Quantity inherits Int\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}",
  "query": "find { Order[] } as {\n  orderId: OrderId\n  // this. fails in anonymous scope - there is no named variable\n  total: Decimal = this.basePrice * this.quantity\n}[]",
  "parameters": {},
  "stubs": [{"operationName": "getOrders", "response": "[{\"orderId\": \"ord-1\", \"basePrice\": 25.0, \"quantity\": 4}]", "echoInput": false, "conditionalResponses": []}],
  "expectedJson": "[{\"orderId\": \"ord-1\", \"total\": 100.0}]"
}
```

Error: `Field basePrice does not exist on type AnonymousType...`

---

### 5.5 Use expression types as traversal nodes with ::

**Impact: MEDIUM (enables readable, reusable paths through related data using named semantic types)**

Expression types can serve as traversal nodes in `::` chains. Because expression types are themselves types, they can be used wherever a type is expected — including as an intermediate node in a `::` traversal.

This allows you to express paths like "the email of the primary contact" as a single reusable named type.

##### Chaining :: through an expression type

Define an expression type, then chain `::` to reach a field on the result:

```taxi
type PrimaryContact inherits Contact =
    (contacts: Contact[]) -> contacts.single((IsPrimary) -> IsPrimary == true)

// Chain :: to navigate to a field on PrimaryContact
type PrimaryEmail inherits EmailAddress = PrimaryContact::EmailAddress
```

In a query, simply request the terminal type:

```taxi
find { Case[] } as {
    primaryEmail: PrimaryEmail    // engine evaluates PrimaryContact then traverses to EmailAddress
}[]
```

##### Using expression types inline in :: chains

Expression types can also be used directly in inline projections without defining a new named type:

```taxi
find { ... } as {
    // Navigate through PrimaryContact to get its email
    contactEmail: PrimaryContact::EmailAddress
    contactName: PrimaryContact::ContactName
}
```

##### Multi-hop chains

Chain multiple expression types together:

```taxi
type PrimaryApplicant inherits Applicant =
    (applicants: Applicant[]) -> applicants.single((IsPrimary) -> IsPrimary == true)

// Navigate: resolve PrimaryApplicant, then get its EmailAddress
type PrimaryApplicantEmail inherits EmailAddress = PrimaryApplicant::EmailAddress
```

```taxi
find { Application[] } as {
    mainEmail: PrimaryApplicantEmail
    // Or inline:
    altEmail: PrimaryApplicant::EmailAddress
}[]
```

##### Why this matters

Without expression types, you'd need to inline the filter logic in every query:

```taxi
// Without expression types — repeated in every query
find { ... } as {
    email: Contact[].single((IsPrimary) -> IsPrimary == true)::EmailAddress
}

// With expression types — readable, defined once
find { ... } as {
    email: PrimaryContact::EmailAddress
}
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/31RQW7CMBD8ympPIKU8wFKqQtUDPVSVeow5OMkCbh07tZ0KFOXvtQmBBNGe7N0dz8yOW3TFniqBDCtTkoJno70oPLRcA2hRERtab6EAqfdkpXfw4a3UuwgKr6Vi8BKPZVlacu4eTLp3KythjwzWw_WKWxmjSGiuO679sSY4IwY7F-DQSCPnrOgrdzGZbebw8AhDf-GCuqLZ7CJ5Gl8NpCl429B8qnra5ao5WS29scbYeIwJfjdkjyHPnfwh3ed4x2bgyeIIoD3HzHGpZEEckyFSjiJ2nuggqlrRojBVnI6SjN6hS26JViaf0OQm_4dkK5QLLJFkc_qArdRlb7we5cEm6QRg2LUWNkh6sg5Z2yXofJOHa7ZJkA41FZ7KV2d0SKPlOCbj-Md6kdWFiL7WJTLdKBVErPkMVH3Z_QJT7I2JsgIAAA==)

```json
{
  "schema": "model Contact {\n  name: ContactName inherits String\n  email: EmailAddress inherits String\n  isPrimary: IsPrimary inherits Boolean\n}\ntype PrimaryContact inherits Contact =\n  (contacts: Contact[]) -> contacts.single((IsPrimary) -> IsPrimary == true)\ntype PrimaryEmail inherits EmailAddress = PrimaryContact::EmailAddress",
  "query": "given {\n  contacts: Contact[] = [\n    { name: \"Alice\", email: \"alice@example.com\", isPrimary: true },\n    { name: \"Bob\", email: \"bob@example.com\", isPrimary: false }\n  ]\n}\nfind {\n  primaryEmail: PrimaryEmail\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\"primaryEmail\": \"alice@example.com\"}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/31Ry27CMBD8ldWeQEr5AEupChUHekCVesQcnGQBt46d2k4FivLvtRMCAdGevI_xzHjcoMsPVApkWJqCFLwa7UXuoeEaQIuS2DBahwakPpCV3sGHt1LvIyjclorBMh7zorDk3COYdO9WlsKeGKyG8opbGKNIaK5brv2pIjgjBjsX4DBII-ck7zt3MbnZTuHpGYb5zAV1RZPJRbJbXw2kKXhb0xQT_K7JnkIOe_lDun__A3pIYRNXAM05Ho5zJXPimAxRcBRx8kJHUVaKZrkp43aUQNSENrknWpjshiYz2T8kO6FcYIkk2y64ndRFb7zqMcue6DZLxsZfNUKvOxf34NHvB5GQUyVsqD1Zh6xpE3S-zkK52SZIx4pyT8WbMzok2XAcG-H4ZzQXYCeDo1CjogsGvlYFMl0rFQxY8xlk-rb9BYqmTIjCAgAA)

```json
{
  "schema": "model Contact {\n  name: ContactName inherits String\n  email: EmailAddress inherits String\n  isPrimary: IsPrimary inherits Boolean\n}\ntype PrimaryContact inherits Contact =\n  (contacts: Contact[]) -> contacts.single((IsPrimary) -> IsPrimary == true)",
  "query": "given {\n  contacts: Contact[] = [\n    { name: \"Alice\", email: \"alice@example.com\", isPrimary: true },\n    { name: \"Bob\", email: \"bob@example.com\", isPrimary: false }\n  ]\n}\nfind {\n  primaryEmail: PrimaryContact::EmailAddress\n  primaryName: PrimaryContact::ContactName\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\"primaryEmail\": \"alice@example.com\", \"primaryName\": \"Alice\"}"
}
```

---

### 5.6 Define reusable expression types in the schema

**Impact: MEDIUM (eliminates duplication and ensures consistent business logic across queries)**

Expression types define reusable calculations as types in the schema. Once defined, they're used like any other type in queries — the query engine evaluates the expression automatically whenever the type is needed.

##### Preferred form: declare explicit inputs

When defining expression types, declare the input parameters explicitly. This makes the expression self-documenting, defines a clear scope boundary for evaluation, and prevents ambiguity when the same semantic type appears in multiple contexts.

```taxi
// Explicit inputs: each parameter is named with its type
type RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold
type UtilizationRate inherits Decimal = (sold: SoldSeats, total: TotalSeats) -> sold / total
```

Both variable names (`total`, `sold`) and type names (`TotalSeats`, `SoldSeats`) can be used in the expression body — they are equivalent:

```taxi
// These are equivalent:
type RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold
type RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> TotalSeats - SoldSeats
```

##### Without explicit inputs (implicit discovery)

Expression types can also omit the input list, letting the engine discover values from context:

```taxi
// Implicit — engine discovers TotalSeats and SoldSeats from the current scope
type RemainingSeats inherits Int = TotalSeats - SoldSeats
```

This is shorter but less explicit about dependencies. Use explicit inputs for shared/schema-defined types.

##### Using expression types in queries

Once defined, use expression types like any other type:

```taxi
find { Flight[] } as {
    flightNumber: FlightNumber
    remaining: RemainingSeats   // engine evaluates the expression automatically
    utilization: UtilizationRate
}[]
```

##### Composing expression types

Expression types can reference other expression types:

```taxi
type DiscountedPrice inherits Decimal = (p: Price, r: DiscountRate) -> p * (1 - r)
type TaxAmount inherits Decimal = (dp: DiscountedPrice, t: TaxRate) -> dp * t
type FinalPrice inherits Decimal = (dp: DiscountedPrice, t: TaxAmount) -> dp + t
```

##### Conditional expression types

```taxi
type CustomerSegment inherits String = when {
    LifetimeValue > 10000 && LastOrderDays < 30 -> "VIP Active"
    LifetimeValue > 10000                        -> "VIP Dormant"
    LastOrderDays < 90                           -> "Regular Active"
    else                                         -> "At Risk"
}
```

##### Key rule: expression types reference types, not field names

**Incorrect (uses raw field names):**

```taxi
// Field names are not valid in expression type definitions
type Bad inherits Int = this.totalSeats - this.soldSeats
```

**Correct (uses semantic types):**

```taxi
type RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold
```

See [expressions-scoping-rules.md](./expressions-scoping-rules.md) for details on how `this.field` access works in different contexts.

##### Validation

**Example 1: Expression type with explicit inputs**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41SwUrDQBD9lWFPFiK2iggBBUWEehBs9dT0sE2m7epmN-5ORA35d2eTNK21oLeZeS9v3rxNJXy6xlyKWOQ2Qw13Wq3WBFViAJZN_VDmC3Rxh7QdKLNGp8jDlJwyq8AmS1JPUZKP4amvt8yxoUDzVmcda7op90h1YuizQJiwMWVY_jcJLuGoWbi7K2rEd3QHcHzV-oLjBuuEn0lp9SVJWTORhFvlW0xVzmxW35OK4Ne6Rj3Q4KQFE-PRvasUu6ymXdeEaQt0zUZYIbW4PxpsYp3N-WwRibcS3Sc_xlKZDKoehBqk_-tRAuo2kcV76bH8bM4LCulkjoTOi7iqI-GpXHA5q0Rv8IEJbGFrkz9z6AvL5_F8ViVi10QiYkjE490oEREX298gAKfDYRj2jx5mo_NhHcFBlZvri0MqZwdUeFaHgzBd27EpShLxUmqPkUityVQ4ROpJZztcOK_nzP4oMCXM7r01f9_Spxnm_3D9g9_a8yTT13EmYlNqzfE7-wIG2rb-BpaFCAp-AwAA=)

```json
{
  "schema": "model Flight {\n  flightNumber: FlightNumber inherits String\n  totalSeats: TotalSeats inherits Int\n  soldSeats: SoldSeats inherits Int\n}\ntype RemainingSeats inherits Int = (total: TotalSeats, sold: SoldSeats) -> total - sold\ntype UtilizationRate inherits Decimal = (sold: SoldSeats, total: TotalSeats) -> sold / total\nservice FlightService {\n  operation getFlights(): Flight[]\n}",
  "query": "find { Flight[] } as {\n  flightNumber: FlightNumber\n  remaining: RemainingSeats\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFlights",
      "response": "[{\"flightNumber\": \"QF1\", \"totalSeats\": 200, \"soldSeats\": 150}, {\"flightNumber\": \"BA7\", \"totalSeats\": 300, \"soldSeats\": 300}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"flightNumber\": \"QF1\", \"remaining\": 50}, {\"flightNumber\": \"BA7\", \"remaining\": 0}]"
}
```

**Example 2: Composed expression types with explicit inputs**

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5VRy07DMBD8lZVPLYSqrThFAgkJCZUDQi1SD0kOJnFbQ2Ibe4NaRfl31qnpiyLEyd7x7O7MuGEuX4mKs5hVuhAlPFtd1DlCkyoAxSsRf0NPVIBUK2ElOpihlWrpScbKvGPRsX-_F7mseOkJyNdTjkR52V7OkNpU4cYIz7irdK3wBwduoGfClgjsblgfrm7BwAXYMKJjzCWuiPDHFIz3C8OcS8BUOWE_vZlgfBbKLhJthOUotYKlwEBwvf4upSQjNyxiH7WwG0p1IVUBzf4VWuDul3RP0wzhHajsEI28jI980soko6WGWxqDwjoWN23EHNavdE0atpPt95CsA_HUZ4UzmmzTQ9KkzAtLWQwpm8uCmCmL6N4p8_BoOBwMPRI-1mPDwaiN4Kj3gZ_pHZ_tHbdevchXeqJMjSxe8NKJiOVaFdKr5uU0SPR2sjYj9tqIHEXx6LT6v-5tHUofaAd45N82fH19Omrskc6VQ56_TwoWq7os6YusfiPd27L9AgLefuZ_AwAA=)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Decimal\n  taxRate: TaxRate inherits Decimal\n}\ntype TaxAmount inherits Decimal = (p: Price, r: TaxRate) -> p * r\ntype PriceWithTax inherits Decimal = (p: Price, t: TaxAmount) -> p + t\nservice ProductService {\n  operation getProducts(): Product[]\n}",
  "query": "find { Product[] } as {\n  name: ProductName\n  price: Price\n  tax: TaxAmount\n  total: PriceWithTax\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getProducts",
      "response": "[{\"name\": \"Widget\", \"price\": 100.0, \"taxRate\": 0.1}, {\"name\": \"Gadget\", \"price\": 200.0, \"taxRate\": 0.2}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"name\": \"Widget\", \"price\": 100.0, \"tax\": 10.0, \"total\": 110.0}, {\"name\": \"Gadget\", \"price\": 200.0, \"tax\": 40.0, \"total\": 240.0}]"
}
```

---

### 5.7 Use when expressions for conditional logic

**Impact: MEDIUM (enables dynamic categorization and conditional field computation)**

`when` provides conditional logic in projections and expression types. Always include an `else` branch.

##### Inline when in queries

```taxi
find { Order[] } as {
    orderId: OrderId
    tier: String = when {
        OrderTotal > 1000 -> "Premium"
        OrderTotal > 100  -> "Standard"
        else              -> "Basic"
    }
}[]
```

##### Schema-defined when expression types

Define reusable conditional logic as types:

```taxi
type CustomerSegment inherits String = when {
    LifetimeValue > 10000 && LastOrderDays < 30 -> "VIP Active"
    LifetimeValue > 10000                        -> "VIP Dormant"
    LastOrderDays < 90                           -> "Regular Active"
    else                                         -> "At Risk"
}
```

##### Key rules

- **Always include an `else` branch** — missing `else` is an error
- Conditions use comparison operators: `==`, `!=`, `>`, `<`, `>=`, `<=`
- Combine conditions with `&&` and `||`
- Conditions are evaluated top-to-bottom; first match wins
- Expression types can only reference **types**, not field names

**Incorrect (missing else):**

```taxi
// ❌ Missing else branch
tier: String = when {
    OrderTotal > 1000 -> "Premium"
    OrderTotal > 100  -> "Standard"
}
```

**Correct (with else):**

```taxi
// ✅ Always include else
tier: String = when {
    OrderTotal > 1000 -> "Premium"
    OrderTotal > 100  -> "Standard"
    else              -> "Basic"
}
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WRPU/DMBCG/8rJE0hBSkFdItGBLQwUUbYkg7GPxpDYwXb4UOT/zuWjaWlplvjsx/ZzrzvmRIk1ZwmrjcQK1laihS7XAEomY5lKULpEq7yDjbdKb/tlbzyvJuK5H++hVPtch1w7tJ9K4MhspmI42zRouVdGwxb9sOwuLqfDsoI2s4h9tGh/SOxVaQndbg0CcHciOAgptMkkCLfwVaIeOTiUXMEijmO4WkHOHi3Wqq1z9j8FI7XxXEtu5Q7DyiH8+QbsjjslRoZaD1lBLTTc8ho9dceSLkTM+faFhlnH5gAeCKAm5xhol0XXGAqPprMuZ4puTuiC9SJnEf2H4PupxTKOQwSHyPURsjwGbk6AOPSmKEqT6qb1LHnl1GDEhNFS9Ya8epqEevUiFER/Nyg8yntn9DlLeo1xZk75jOsMTgmeMZ6x/XsM5s5z8Z5Klui2qihya97IbSzDL9XG30zgAgAA)

```json
{
   "schema": "model Order {\n  id: OrderId inherits String\n  total: OrderTotal inherits Int\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}",
   "query": "find { Order[] } as {\n  id: OrderId\n  tier: String = when {\n    OrderTotal > 1000 -> \"Premium\"\n    OrderTotal > 100  -> \"Standard\"\n    else              -> \"Basic\"\n  }\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getOrders", "response": "[{\"id\": \"O1\", \"total\": 1500}, {\"id\": \"O2\", \"total\": 50}, {\"id\": \"O3\", \"total\": 500}]"}],
   "expectedJson": "[{\"id\": \"O1\", \"tier\": \"Premium\"}, {\"id\": \"O2\", \"tier\": \"Basic\"}, {\"id\": \"O3\", \"tier\": \"Standard\"}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/41STU/DMAz9K1YOE0idNA67VAyJCQmNAyB24LDukKUeDcuSkqSDqep/x2m77zFxSmy/Z/u9pGROZLjkLGZLk6KCV2vSQngoEw2g+RLjTeqZApA6Qyu9g7G3Un8EUG6lqFF07Ooj7UNR6rE3YhFTXF92gKExCrlOdJVov84R7ldcKj6TSvr18RwYwHeGutlq02owAG8LhE6nHX4L/R507yBhbS+F0IVhkX6gT9g56iG4waBybeGl8GDmUFNCjTZ1aFdhVOvJuA3rvUyOlntpNNC8FuCurrcGTqbUgkXsq0C7JsPnUqdQ7qpQAXd/GB+SfM+g+MAu6juZUuecW8J6tI7FZRUx54sZXScl2+4WmtHsvQ2JZ9HlhrRRYVImLExPWEwOvMvGu4ju9TuH9E0vhO3LhkSwsorggPnIzzL/Q30wJpNigesjdv+IO+f0UlXQjSIzI50XnsV1MmLC6FQGvVy9teKCEdNqSuifHIXH9MkZfVHxvuFN9dy/uqT8QodT4pHuU+7hf6yVO8/FYpSyWBdK0Qew5pO0NWH1C6drjUjdAwAA)

```json
{
   "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Int\n  inStock: InStock inherits Boolean\n}\ntype Availability inherits String = when {\n  InStock == true && Price < 50 -> \"Available - Budget\"\n  InStock == true -> \"Available\"\n  else -> \"Out of Stock\"\n}\nservice ProductService {\n  operation getProducts(): Product[]\n}",
   "query": "find { Product[] } as {\n  name: ProductName\n  availability: Availability\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getProducts", "response": "[{\"name\": \"Widget\", \"price\": 10, \"inStock\": true}, {\"name\": \"Gadget\", \"price\": 100, \"inStock\": true}, {\"name\": \"Doohickey\", \"price\": 5, \"inStock\": false}]"}],
   "expectedJson": "[{\"name\": \"Widget\", \"availability\": \"Available - Budget\"}, {\"name\": \"Gadget\", \"availability\": \"Available\"}, {\"name\": \"Doohickey\", \"availability\": \"Out of Stock\"}]"
}
```

---

## 6. Standard Library

**Impact: MEDIUM**

Built-in functions available in all queries: string manipulation (`upperCase`, `concat`, `trim`), math aggregation (`sum`, `min`, `max`, `round`), collection operations (`filter`, `single`, `getAtIndex`, `map`), date/time helpers (`now`, `parseDate`), type transformation (`convert`, `toRawType`), and boolean aggregation (`allOf`, `anyOf`, `noneOf`).

---

### 6.1 Use allOf, anyOf, noneOf for boolean aggregation

**Impact: MEDIUM (simplifies complex boolean logic into reusable, readable expressions)**

Taxi provides three boolean aggregation functions for combining multiple boolean values into a single result. They are best used as expression types in the schema for reusable business rules.

##### Functions

| Function | Description | Equivalent |
|----------|-------------|------------|
| `allOf(Boolean...)` | True if ALL are true | Logical AND |
| `anyOf(Boolean...)` | True if ANY are true | Logical OR |
| `noneOf(Boolean...)` | True if NONE are true | Logical NOR |

##### As schema-defined expression types (preferred)

```taxi
type HasPremiumAccess inherits Boolean by allOf(IsActive, HasSufficientFunds, IsVerified)
type IsVerified inherits Boolean by anyOf(HasEmailVerification, HasPhoneVerification)
type HasNoElevatedRoles inherits Boolean by noneOf(IsAdmin, IsEditor)
```

##### Inline in queries

```taxi
find { Customer[] } as {
    eligible: Boolean = allOf(IsVerified, HasSufficientIncome, HasNoCCJs)
}[]
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5VSy07DMBD8FcsnKuULfGuREOUAiEpckhxcZ9MYHDv4EVFF+XfWbdIGEkS57WPWMzvrljpRQc0po7UpQJHb4LypwZI204SIPnvkNbBzL2ZE6gqs9I5svJV6F9HSLYWXe0Su++iCWhmjgOsIq7jbhLKUQoL2d0EXjpH7SW12VLpXrJUSisgxxDPQLtP+0EB899lCLUO9FALc9FWyPRCu1FN5M2hOZsQkI7ZFph3YvRRwNmTT50fPTAOWe2k02YEfEO5mcfEvzVEgTehHAHtA50upC9KO2qQj3P11gd7Kb+uxycLIlObI1XCLMx6VUNZ2CXU+bDFMW3rWGx9FNWPVOGjBNQY3xk7aZnSsJ6OMZHSpcPWMJhgOHyA2vA0Qa9Nrj7uXgw7VLiGzNCuz/S9JyZX7jSV6AqIya90ET9kJSoXRhYxecPXS7x1Nyrsc0Z8NCA/FgzP6GjN+3ubqBecGj/qOop3n4n1dUKaDUnhXa95Q1intvgBhpaw8zwMAAA==)

```json
{
   "schema": "model Customer {\n  customerName: CustomerName inherits String\n  isActive: IsActive inherits Boolean\n  hasSufficientFunds: HasSufficientFunds inherits Boolean\n  isVerified: IsVerified inherits Boolean\n}\ntype HasPremiumAccess inherits Boolean by allOf(IsActive, HasSufficientFunds, IsVerified)\nservice CustomerService {\n  operation getCustomers(): Customer[]\n}",
   "query": "find { Customer[] } as {\n  customerName: CustomerName\n  hasPremiumAccess: HasPremiumAccess\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getCustomers", "response": "[{\"customerName\": \"Alice\", \"isActive\": true, \"hasSufficientFunds\": true, \"isVerified\": true}, {\"customerName\": \"Bob\", \"isActive\": true, \"hasSufficientFunds\": false, \"isVerified\": true}]"}],
   "expectedJson": "[{\"customerName\": \"Alice\", \"hasPremiumAccess\": true}, {\"customerName\": \"Bob\", \"hasPremiumAccess\": false}]"
}
```

---

### 6.2 Collection functions in TaxiQL's standard library

**Impact: HIGH (essential for filtering, transforming, and extracting data from arrays)**

TaxiQL provides a rich set of collection functions for working with arrays.

##### Core collection functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `filter` | `T[].filter((T) -> Boolean): T[]` | Keep items matching predicate |
| `map` | `T[].map((T) -> A): A[]` | Transform each item |
| `first` | `T[].first(): T` | First item (error if empty) |
| `last` | `T[].last(): T` | Last item (error if empty) |
| `getAtIndex` | `T[].getAtIndex(Int): T` | Item at index (error if out of bounds) |
| `single` | `T[].single((T) -> Boolean): T` | One item matching predicate (error if 0 or 2+) |
| `singleBy` | `T[].singleBy((T) -> A, A): T` | Like single, cached for repeated lookups |
| `exactlyOne` | `T[].exactlyOne(): T` | Sole item in collection (error if not exactly 1) |
| `contains` | `T[].contains(T): Boolean` | Check if collection contains item |
| `intersection` | `intersection(T[], T[]): T[]` | Common elements between two collections |

##### Boolean collection functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `all` | `T[].all((T) -> Boolean): Boolean` | True if all match |
| `any` | `T[].any((T) -> Boolean): Boolean` | True if any match |
| `none` | `T[].none((T) -> Boolean): Boolean` | True if none match |

##### Aggregation functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `sum` | `T[].sum((T) -> A): A` | Sum values from callback |
| `min` | `T[].min((T) -> A): A` | Minimum value from callback |
| `max` | `T[].max((T) -> A): A` | Maximum value from callback |
| `fold` | `T[].fold(A, (T, A) -> A): A` | Reduce to accumulated value |
| `reduce` | `T[].reduce((T, A) -> A): A` | Like fold without explicit initial |

##### Other collection functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `joinToString` | `T[].joinToString(String, String?, String?): String` | Join to string |
| `listOf` | `listOf(T...): T[]` | Create array from values |

##### Usage patterns

**Filtering:**
```taxi
find { Order[] } as {
    pendingOrders: Order[].filter((OrderStatus) -> OrderStatus == 'PENDING')
    highValue: Order[].filter((OrderTotal) -> OrderTotal > 1000)
}[]
```

**Extracting single items:**
```taxi
find { Customer[] } as {
    primaryAddress: Address[].single((IsPrimary) -> IsPrimary == true)
}[]
```

**Mapping:**
```taxi
find { Invoice[] } as {
    supplierNames: Supplier[].map((Supplier) -> Supplier::SupplierName)
}[]
```

**Checking membership:**
```taxi
given {
    order: Order = { id: 'order-1', status: 'PENDING' }
}
find {
    isValidStatus: listOf('PENDING', 'PROCESSING', 'SHIPPED').contains(order.status)
}
```

##### Lambda parameter best practice

Use type-based access in lambda parameters:

```taxi
// ✅ Preferred: type-based
Order[].filter((OrderStatus) -> OrderStatus == 'PENDING')

// ⚠️ Less preferred: field-based
Order[].filter((o) -> o.status == 'PENDING')
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/51QQW6DMBD8irWnRKIRaZQLUnoiaukhRaI3zMHFTuLWmNQ2VSvE32swDSSiUlWfvDs7uzNTg86PrCAQQFFSJtCTokyhGkuEOA1cGVHE5ZEpbjRKjOLy0MLaEFPpfiTpiqkxUxoi+qnn9j8MhSznBRFYNuDBe8XUl5Vx4B9MOgHIsdIMbVDqGu2rO2UYyiUG7ywDQ7zdhdHuvm32R5drf+H7qPEmyLeX5OQhiuNtOCKv_d_Jqz9cdmxHzqxHLPdc0itniz0XhqnZbJTiHN3cXaS62YxvzF1eJ6JIwSxXQ1A3HmhTvdhvmnnAPk8sN4w-6lLaRF109Y8NDJxiOAc4tJ0fBw2OBryz1sIu1g7o05lavvr38mE7lhm01kj-FlEIZCWEda7KV-vPlc03tXXGpsICAAA=)

```json
{
  "schema": "model Order {\n  id: OrderId inherits String\n  status: OrderStatus inherits String\n  total: OrderTotal inherits Decimal\n}",
  "query": "given {\n    Order[] = [\n        { id: \"o1\", status: \"PENDING\", total: 150.00 },\n        { id: \"o2\", status: \"SHIPPED\", total: 500.00 },\n        { id: \"o3\", status: \"PENDING\", total: 1500.00 }\n    ]\n}\nfind {\n    Order[].filter((OrderStatus) -> OrderStatus == \"PENDING\")\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n   {\n      \"id\": \"o1\",\n      \"status\": \"PENDING\",\n      \"total\": 150.00\n   },\n   {\n      \"id\": \"o3\",\n      \"status\": \"PENDING\",\n      \"total\": 1500.00\n   }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/32QP0_DMBDFv8rpplYKVdpKDJZg6tIulYAtzmDiozU4dvEfBIry3XHqRM2A8HRn__zeu-vQN2dqBTJsrSQNRyfJQccNgJIst3sJypzJqeDhOThlTsNzsEHokXgZ6hu0o0a1QnPTY4GfkdxP0j-pLzJZGfKvqoYHqPLFcLqrJUe75lhM-uuyXJUl9MUf3GbGbf7htjNuO3IZq1NGbt6UkVOyK_ZEKWskNk2Sco6JVz62i8Vt6CXcPc52sMwzX4QTLQVyHlnXF-hDfE1lVRdI3xdqAsmDtyZtJbtynNtyZHA_xMxiPojmYy-Rmah10nb2PSnktv8F1Rws1sEBAAA=)

```json
{
  "schema": "model Order {\n  id: OrderId inherits String\n  total: OrderTotal inherits Decimal\n}",
  "query": "given {\n    Order[] = [\n        { id: \"o1\", total: 100.00 },\n        { id: \"o2\", total: 200.00 },\n        { id: \"o3\", total: 300.00 }\n    ]\n}\nfind {\n    totalRevenue: Decimal = Order[].sum((OrderTotal) -> OrderTotal)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n   \"totalRevenue\": 600.0\n}"
}
```

---

### 6.3 Date and time functions in TaxiQL's standard library

**Impact: MEDIUM (enables date arithmetic and current-time references in queries)**

Taxi provides functions for working with dates and times. These are evaluated locally.

##### Available date functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `now` | `now(): Instant` | Current point in time (with timezone) |
| `currentDate` | `currentDate(): Date` | Current date |
| `currentDateTime` | `currentDateTime(): DateTime` | Current date and time |
| `currentTime` | `currentTime(): Time` | Current time |
| `addDays` | `addDays(T, Int): T` | Add days to date/time |
| `addMinutes` | `addMinutes(T, Int): T` | Add minutes |
| `addSeconds` | `addSeconds(T, Int): T` | Add seconds |
| `parseDate` | `parseDate(String): T` | Parse string to date type |

##### Importing date functions

Date functions require imports:

```taxi
import taxi.stdlib.dates.currentDate
import taxi.stdlib.dates.now
```

##### Usage in projections

```taxi
find { Order[] } as {
    orderId: OrderId
    processedAt: Instant = now()
    dueDate: Date = addDays(currentDate(), 30)
}[]
```

##### Date formatting with @Format

Control how dates are displayed using `@Format`:

```taxi
// Inline format annotation
find { Event } as {
    @Format("yyyy-MM-dd'T'HH:mm:ssXXX")
    isoTimestamp: Instant

    @Format("dd/MM/yyyy hh:mm a z")
    readableTimestamp: Instant
}

// Format defined on a named type
@Format("yyyy-MM-dd'T'HH:mm:ssXXX")
type FormattedInstant inherits Instant

find { Event } as {
    withFormattedType: (FormattedInstant) Instant
}
```

##### Date comparisons in filters and when expressions

```taxi
find { Order[] } as {
    status: String = when {
        OrderDate < currentDate() -> "Past"
        OrderDate == currentDate() -> "Today"
        else -> "Future"
    }
}[]
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WOQUvDQBCF/8owl1pIIE31siB4KKUVessh6HpYs2Mb7U7SnY1YQv67A0Hx4ju9gTfveyNKc6Lg0CBmeBkoXtUe209iGCG1gSS50BvYsxpOcA-9i0Ibl-jGYlmU63xV5uVdtbo168IUxZPF5WT5rWUPo2VQPWy7GFzS_FWVHw6594tqsduZEIxIXdf6M0db6aofKPxSLU-6TsEuUKIoaMYpQ0nDq9rnlwzpq6cmkX-UjnX_zLX4t82igf8Gz_0aaz72Hg0P57PiYveupfM5fQM0FoldKQEAAA==)

```json
{
  "schema": "",
  "query": "given { timestamp: Instant = parseDate(\"2023-12-25T14:30:00Z\")}\nfind {\n    @Format(\"yyyy-MM-dd'T'HH:mm:ssXXX\")\n    isoTimestamp : Instant\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n   \"isoTimestamp\": \"2023-12-25T14:30:00Z\"\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/12OwQ6CMAyGX6XpSZKRAOJliTcu-grOw2RVURi4DSMhvLsjECL20Pxt_vb_erT5nSqJHJHhqyXTeXkr3qShh9ooMpl0xGHssIdGGkuj3ghMoiQNoziMdwKDQehroRX0QoOvv8tpqVpaPZNKZbKzm8XMYBsFQg8exQfJihwZi7wfGFrXXrw8nRnSp6HckTraWnvYKVHg8kUghzUdmy0zwK8hCeNU4JRpncyfB4Vct2XpEUz98EHTOHwBNuxdACoBAAA=)

```json
{
  "schema": "",
  "query": "given { orderDate: Date = parseDate(\"2024-01-15\")}\nfind {\n    orderDate: Date\n    dueDate: Date = addDays(orderDate, 30)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n   \"orderDate\": \"2024-01-15\",\n   \"dueDate\": \"2024-02-14\"\n}"
}
```

---

### 6.4 Math aggregation and rounding functions in TaxiQL's standard library

**Impact: MEDIUM (essential for computing totals, extremes, and formatted numeric values)**

TaxiQL's standard library provides aggregation functions for collections and a rounding function for Decimal values.

##### Aggregation functions

These functions operate on collections and take a callback to extract the numeric value:

| Function | Signature | Description |
|----------|-----------|-------------|
| `sum` | `T[].sum((T) -> A): A` | Sum all values from callback |
| `min` | `T[].min((T) -> A): A` | Minimum value from callback |
| `max` | `T[].max((T) -> A): A` | Maximum value from callback |

##### Rounding function

| Function | Signature | Description |
|----------|-----------|-------------|
| `round` | `Decimal.round(precision: Int): Decimal` | Round to N decimal places |

##### Aggregation usage

```taxi
find { Order[] } as {
    orderId: OrderId
    totalSpent: Decimal = OrderItem[].sum((item) -> item::Price * item::Quantity)
    cheapestItem: Decimal = OrderItem[].min((item) -> item::Price)
    mostExpensive: Decimal = OrderItem[].max((item) -> item::Price)
}[]
```

When the callback extracts a single typed value (not a computed expression), prefer the type-based form:

```taxi
// ✅ Preferred: type-based callback
OrderItem[].sum((Price) -> Price)
OrderItem[].max((Price) -> Price)

// ⚠️ Less preferred: field-based (couples to field name)
OrderItem[].sum((item) -> item.price)
```

##### Rounding usage

`round()` is an extension function called on a `Decimal` value:

```taxi
find { Product[] } as {
    name: ProductName
    price: Price
    displayPrice: Decimal = Price.round(2)   // 2 decimal places
}[]
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4WRzWrDMBCEX0XolIBq0gRTMLTQn0t6aFNyjHJQ7W2s1lo7ktwmGL97pSjBKiTUPnjl3Y-ZWXXU5CUoQTOq6gIq8qoL0HMLinQcCUGhICP-_OIqIrEELa0hS6slbvxEo2XuRhb-M_SfIJdKVH5g2wq00u4z8nashrE5Wo49ZXTbgt47Exv5DRikyeBltSa3ZBV--qc7-uL0vmkq4JSdbFwn6YRFkjPSs3Pcg0D3RuAkuUljML0APjrneh-Bs2TyR3FK-sCtXTKOHxKLUx5bW1EtG0CbnRbkckUpE9Oq0eiwyjG5ugtLHQe4lJsSjF0E1fO4ErtLeFX__EtLPEf762mEdvktaEOzrmfU2Pbdlas1o7BrILdQPJsa3QUeonI6ROXULTOZpiw04hi-dVhfaEUWfcffSFA3VuRf84Jm2FaVM6PrTycZjv0vITY5uMICAAA=)

```json
{
  "schema": "model OrderItem {\n  name: ItemName inherits String\n  price: Price inherits Decimal\n  quantity: Quantity inherits Int\n}",
  "query": "given {\n    OrderItem[] = [\n        { name: \"Apple\", price: 1.50, quantity: 3 },\n        { name: \"Banana\", price: 0.75, quantity: 5 },\n        { name: \"Cherry\", price: 3.00, quantity: 2 }\n    ]\n}\nfind {\n    totalSpent: Decimal = OrderItem[].sum((Price) -> Price)\n    highestPrice: Decimal = OrderItem[].max((Price) -> Price)\n    lowestPrice: Decimal = OrderItem[].min((Price) -> Price)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"totalSpent\": 5.25,\n  \"highestPrice\": 3.00,\n  \"lowestPrice\": 0.75\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQMU_DMBCF_4rlCaSoaisYEomtEoIBVWJgiDMY-2hMEzvYDqKK8t85242aQpsl8fPLu3ffQJ2ooeW0oK2R0JCtNbIXngxME6J5C8UkveCBKF2DVd6RV2-V3gVTZ5WILnyd7jcgVMsbpkea0a8e7AFH7NQ36BRNptiyIg-kTFJ4huNURt-U3IFnNJtG5Is8J2N2yfvI_3jXd9fNG2NqJfZwmPmTPbkrbM30h9ISfzrVHAl3U_l_ZJI8Z5EUa3otQW7TxZEKbhyFRby9Wd3iwLJCUB23mOXBOloMY0ad79_xs6wyCj8dCA_y2RmNKCOxAZcJTRg958VoLBLkwCwI8x5BXy0XyyOe85QTyVlKpHkpZn1_JeaM8SzpWlDMYTpAcJ6L_ZOkhe6bBplY84mbp-P4C9Jh1IixAgAA)

```json
{
  "schema": "model Product {\n  name: ProductName inherits String\n  price: Price inherits Decimal\n}",
  "query": "given {\n    Product[] = [\n        { name: \"Widget\", price: 9.99 },\n        { name: \"Gadget\", price: 24.99 },\n        { name: \"Doohickey\", price: 4.99 }\n    ]\n}\nfind { Product[] } as {\n    name: ProductName\n    price: Price\n    roundedPrice: Decimal = Price.round(1)\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n  { \"name\": \"Widget\", \"price\": 9.99, \"roundedPrice\": 10.0 },\n  { \"name\": \"Gadget\", \"price\": 24.99, \"roundedPrice\": 25.0 },\n  { \"name\": \"Doohickey\", \"price\": 4.99, \"roundedPrice\": 5.0 }\n]"
}
```

---

### 6.5 String functions in TaxiQL's standard library

**Impact: MEDIUM (enables text manipulation and formatting in projections)**

Taxi's standard library provides functions for string manipulation. These are evaluated locally — they don't push computation to source systems. Use regular function call syntax.

##### Available string functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `upperCase` | `upperCase(String): String` | Convert to uppercase |
| `lowerCase` | `lowerCase(String): String` | Convert to lowercase |
| `trim` | `trim(String): String` | Remove leading/trailing whitespace |
| `left` | `left(String, Int): String` | Leftmost N characters |
| `right` | `right(String, Int): String` | Rightmost N characters |
| `mid` | `mid(String, Int, Int): String` | Substring (inclusive start, exclusive end) |
| `length` | `length(String): Int` | String length |
| `replace` | `replace(String, String, String): String` | Replace occurrences |
| `containsString` | `containsString(String, String): Boolean` | Check if contains substring |
| `indexOf` | `indexOf(String, String): Int` | Index of substring |
| `concat` | `concat(Any...): String` | Concatenate values |

##### Usage in projections

```taxi
find { Person[] } as {
    upperName: String = upperCase(FirstName)
    fullName: String = concat(FirstName, ' ', LastName)
    initials: String = concat(FirstName.left(1), LastName.left(1))
    trimmedEmail: String = trim(EmailAddress)
}[]
```

##### Extension function syntax

Some functions can also be called with dot syntax on the value:

```taxi
find { Person[] } as {
    initials: String = concat(FirstName.left(1), LastName.left(1))
}[]
```

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQzU7DMBCEX2W1l7ZSnsASB1qB1KqCih6THNxk2xgcO9gOAkV5dzbND6WUm9ee8X4zDfqsoFKiwNLmpGFHzlsDTWIAjsr58CRLEvA4HkGZgpwKHvbBKXPqdFqOsq38T9UmxpP7UBkNK/bDdN5kK3IyKF58orAjW2maL8SgjFO2Y4TvNbkvBj0qk0MzPUIL0vff1BX/05P0e+Guv1tJT/Mpw+Icrtb6SppZk8nwo4tgBrNoCsW2Nk4ZpJKOx8D7UTRthD7UBz7GDU45Oj2jTmnY5chXlkvg67hJcCo3QQEJ3msuI8GIj2Od/cO+VKFIsI3gr2lpD7csG2vIs6Vjpaywa1PVAcVRak8RcspcdYxSvwxIHXzapqz+rCgLlG+42YFz6nTg3K5XD/3SscGLAPCL9sq6fF7eMnIIuCT2QWZv6xyFYRmX7ewrM/Vj+w1GOME1sAIAAA==)

```json
{
   "schema": "model Person {\n  firstName: FirstName inherits String\n  lastName: LastName inherits String\n}\nservice PersonService {\n  operation getPeople(): Person[]\n}",
   "query": "find { Person[] } as {\n  upperName: String = upperCase(FirstName)\n  fullName: String = concat(FirstName, ' ', LastName)\n}[]",
   "parameters": {},
   "stubs": [{"operationName": "getPeople", "response": "[{\"firstName\": \"Alice\", \"lastName\": \"Smith\"}, {\"firstName\": \"Bob\", \"lastName\": \"Jones\"}]"}],
   "expectedJson": "[{\"upperName\": \"ALICE\", \"fullName\": \"Alice Smith\"}, {\"upperName\": \"BOB\", \"fullName\": \"Bob Jones\"}]"
}
```

---

### 6.6 Type transformation functions in TaxiQL's standard library

**Impact: LOW (enables explicit type conversion and semantic type stripping)**

TaxiQL provides two transformation functions for working with types at runtime.

##### convert

Converts a source value into a target type using only locally available data. No service calls are made.

```taxi
// Convert a source object to a compatible target type
given { src: SourceModel = { ... } }
find {
    tgt: TargetModel = convert(src, TargetModel)
}
```

**Important:** `convert` is less powerful than a standard projection (`A as B`):
- Only the data provided in `source` is considered
- No graph searches or remote invocations are performed
- Types must share compatible field types for conversion to succeed

Use a projection (`as`) when you need service discovery; use `convert` when you want a fast, local-only type coercion.

##### toRawType

Removes semantic typing from a scalar value, returning the underlying primitive. Useful for comparing values that have different semantic types but the same base type.

```taxi
type PriceUSD inherits Decimal
type PriceGBP inherits Decimal

// PriceUSD and PriceGBP cannot be directly compared
// toRawType strips the semantic type so comparison works
given { usd: PriceUSD = 100.0 }
find {
    rawPrice: Decimal = toRawType(usd)   // Returns 100.0 as plain Decimal
}
```

**Notes:**
- Works on scalar values and arrays of scalars
- Does **not** work on objects (Taxi's type system needs to know the structure)
- When called on an array, removes semantic types from all members

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2XOPWvDMBAG4L9y3NSAKc4q6BICpZ1Ck3aJMqjytVZqn1195APh_57DrslQTRL36nkvY7A1tQYVxmtPsPHO0vt2DY5r8i4GWJN1rWk03-fPq82_ORb4m8hfBfp2J2LIkEI1xtVdfYJlWT6WMGj-clxB1gxyvDl_mCZJ8k-TYOzezHknnQ-zs9A8SE1vvGkpkg-o8lBgiOlTrvtDgXTpyUaqXkPHssioa5x1jWqqn5wQjf15qVBxahphfXeUz9NzuAFud0ulGAEAAA==)

```json
{
  "schema": "type PriceUSD inherits Decimal\ntype PriceGBP inherits Decimal",
  "query": "given { usdPrice: PriceUSD = 100.0 }\nfind {\n    rawValue: Decimal = toRawType(usdPrice)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"rawValue\": 100.0\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22OsU7EMAyGX8XyBFKEEGKKdBsLDCyHWAhDSE0vXOseiVOBqr477uXKLbfZ1u_v_ybMYUe9R4v90FAH26GkQDA5BmDfkz1dnnWGyDtKUTJsJUVul8zou_Ifel2Wc-qBQux953h2XOkvPrUkl-mXafqMBr8LpV91bONIDBPkFNYUbHSvLIdCWRyalXN_d3MLM2j9Z-Sm1gJIK3Y12UAYeKQkV4o0p-t1LT34pFihlNFOs8Es5UPHt3eD9HOgINQ85YFV6wjW9lbLreo4XISW-azk8Ci1HKtWLcniw_6xQcul67QzDV9Kruv8BzzD6fSeAQAA)

```json
{
  "schema": "model Source {\n  name: SourceName inherits String\n  value: SourceValue inherits Decimal\n}\nmodel Target {\n  name: SourceName\n  value: SourceValue\n}",
  "query": "given { src: Source = { name: \"test\", value: 42.0 } }\nfind {\n    tgt: Target = convert(src, Target)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"tgt\": { \"name\": \"test\", \"value\": 42.0 }\n}"
}
```

---

## 7. Mutations

**Impact: HIGH**

Write operations never happen implicitly. The `call` directive must be used explicitly to invoke a `write operation`. Covers single vs batch writes and how the engine constructs inputs from the current query context.

---

### 7.1 Write operations require explicit call

**Impact: HIGH (mutations never happen implicitly — prevents accidental data modification)**

Read operations are called implicitly during queries. Write operations (marked with the `write` keyword) must be called explicitly using the `call` directive.

##### Declaring write operations

```taxi
service PersonService {
    // Read operation — called implicitly during queries
    operation findPerson(PersonId): Person

    // Write operation — must be called explicitly
    write operation updatePerson(Person): Person
}
```

##### Invoking mutations

Use `call Service::operationName`:

```taxi
// With given data (as object)
given {
    customer: Customer = { id: 'CUST-001', fullName: 'Alice' }
}
call CustomerDb::saveCustomer

// After finding data
find { Person }
call PersonService::updatePerson

// With a projection before the mutation
find { Film[] } as {
    id: FilmId
    title: Title = upperCase(Title)
    rating: ReviewScore
}
call FilmDatabase::saveFilm
```

##### Single vs batch

The input type determines processing mode:

```taxi
service FilmService {
    // Processes one at a time
    write operation saveSingle(Film): Film

    // Processes in batches
    write operation saveBatch(Film[]): Film[]
}
```

##### Input construction

TaxiQL automatically constructs operation inputs from available data in the query context. The engine matches types from the current scope to the operation's parameter types.

##### Examples

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2WQP0/DMBDFv4p1S0AKUlktMaCyhIGBwoQ7uPbRGBw72E4ARfnvnBuSVoDY/Hzf3b13A0RVYyOBQ+M1WrbuYvINBjYIx5jRfPmpNDOuxmBSZJsUjNtn4rmz9k42eOSy+k2OwkUMvVG4gDe7ack7gch8i0Em4x2LsseZOZsf58cFNAxKeOswfJLtvenR/en2ihXrx83DxWp1WZT/eCXu2pKx4uBSSWtPLHJ+aof2tjJQU8IQgQ9jCTF1O3o+DbAkyFPJ2Y/GgLH1dASqkEJV+8q1XQKeQoclKO+0yd3S3n+Teex23BL80aJKqG+jd9Q+CDBaAGcC5oACSlJzwKl2CCUgHysmqV4rDdwRQRmCf6GBkxy/ACYecUIEAgAA)

```json
{
   "schema": "model Customer {\n  id: CustomerId inherits String\n  fullName: CustomerName inherits String\n}\nservice CustomerDb {\n  write operation saveCustomer(Customer): Customer\n}",
   "query": "given {\n  customer: Customer = { id: 'CUST-001', fullName: 'Alice' }\n}\ncall CustomerDb::saveCustomer",
   "parameters": {},
   "stubs": [{"operationName": "saveCustomer", "response": "", "echoInput": true}],
   "expectedJson": "{\"id\": \"CUST-001\", \"fullName\": \"Alice\"}"
}
```

---

## 8. Streaming

**Impact: MEDIUM**

Using `stream {}` instead of `find {}` for continuous event subscriptions. Covers basic streams, `filterEach`, projections on streams (requires `[]` suffix), and combining streams with union (`|`) and intersection (`&`).

---

### 8.1 Use stream for subscribing to event streams

**Impact: MEDIUM (enables real-time data processing with familiar TaxiQL syntax)**

The `stream` keyword subscribes to a continuous stream of events instead of fetching static data.

##### Basic streams

```taxi
stream { OrderEvent }
```

##### Stream projections

Transform each event as it arrives:

```taxi
stream { StockPrice } as {
    symbol: StockSymbol
    updateReceived: Instant = now()
    currentPrice: StockPrice
}
```

##### Filtering streams

Use `filterEach` to select specific events:

```taxi
stream { StockQuotes.filterEach(StockSymbol -> StockSymbol == 'AAPL') }
```

##### Union streams (either/or)

Emit when either event occurs:

```taxi
stream { TradeEvent | QuoteEvent }
```

##### Intersection streams (both required)

Emit only when both events have occurred:

```taxi
stream { CustomerUpdate & AddressUpdate }
```

##### Key differences from find

- `stream` produces a continuous flow of results, not a single response
- Use `filterEach` instead of `.filter()` for stream filtering
- Projections work the same way — each event is transformed individually
- Streams can use union (`|`) and intersection (`&`) for combining event sources

---

## 9. Publishing Queries

**Impact: MEDIUM**

Wrapping TaxiQL queries in named `query {}` blocks and deploying them as persistent endpoints. Covers publishing as REST API endpoints (`@HttpOperation`), parameterised routes, POST with `@RequestBody`, and stream processor pipelines that continuously consume events (e.g. from Kafka) and optionally expose results as Server-Sent Events.

---

### 9.1 Publish named queries as HTTP endpoints

**Impact: HIGH (exposes query results as reusable REST API endpoints)**

Named queries can be published as HTTP endpoints by annotating them with `@HttpOperation`. This turns a TaxiQL query into a persistent, callable REST API endpoint served by Orbital.

##### Basic HTTP endpoint

```taxi
import taxi.http.HttpOperation

@HttpOperation(method = "GET", url = "/api/q/pending-orders")
query PendingOrders {
    find { Order[].filter((OrderStatus) -> OrderStatus == "PENDING") }
}
```

When deployed, this query is accessible via `GET /api/q/pending-orders` and returns the live results of the query.

##### Parameterized HTTP endpoint

Parameters declared in the query signature are bound from the HTTP request:

```taxi
import taxi.http.HttpOperation
import taxi.http.PathVariable

@HttpOperation(method = "GET", url = "/api/q/customers/{id}")
query GetCustomer(@PathVariable id: CustomerId) {
    find { Customer(CustomerId == id) }
}
```

The parameter `id` is extracted from the URL path and passed into the query.

##### POST endpoint with request body

Use `@RequestBody` to bind parameters from a POST request body:

```taxi
import taxi.http.HttpOperation
import taxi.http.RequestBody

@HttpOperation(method = "POST", url = "/api/q/submit")
query SubmitData(@RequestBody body: StockReport[]) {
    given { body }
    call InventoryService::updateStock
}
```

##### Streaming endpoint

Combine with `stream {}` to create a streaming (Server-Sent Events) endpoint:

```taxi
import taxi.http.HttpOperation

@HttpOperation(url = "/api/q/stock-events", method = "GET")
query StockEventStream {
    stream { StockUpdateEvent } as {
        productId: ProductId
        quantity: Quantity
        timestamp: EventTimestamp
    }[]
}
```

##### Key points

- Import `taxi.http.HttpOperation` to use the annotation
- The `url` and `method` parameters define the REST endpoint
- Query parameters map to URL path variables, query parameters, or request body fields
- Streaming queries publish as Server-Sent Events (SSE) streams
- These queries are persisted and run on every HTTP request, not pre-computed

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/6VQy27CMBD8FWtPIKWgtrdIVFUFgvQACHrDHNx4IW6DHWynoory7908QKBI7aEXy-sZz2MLcHGCBwEhqENmrGdenNQg8T4bzOhYZGiFV0Zz3cFXeMzR-RcjvznhByMxZQsr0bKCa8aUDJsxkkzpBK3yjq29VXpfwc4Ln7uWsq6HLq3k2qH9UjG2vHaoDcw5HNujr2HX67eCmy19hgAoov2mds83bXoH9ImRbMQ4TCdvHAKW27QehyJTw-PQ1HIc-lzXEmyJWlKkxqbx3yktWXH2G-xU6tH2eleF-uzu6abgqLJYTubjaD4lcVY2KTNhBUUiZQiLMgDn83e6bgq4dJwTgXpcmtIviy4ztB963hQclOQQkvzivurDoVlw83axLAN2TX3oUtezaLmcjDvUx99UtxQH48REOss9hDuROgwgNrSzKrxIV23WqtW23BL7lGHsUb46o_9R4M9UhMSfkYRQ52lKm7bmg3ybsfwBRSO-8P8CAAA=)

```json
{
  "schema": "import taxi.http.HttpOperation\nimport taxi.http.RequestBody\n\nmodel Order {\n  id: OrderId inherits String\n  status: OrderStatus inherits String\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}",
  "query": "@HttpOperation(method = \"GET\", url = \"/api/q/orders\")\nquery PendingOrders {\n  find { Order[].filter((OrderStatus) -> OrderStatus == \"PENDING\") }\n}",
  "parameters": {},
  "stubs": [
    {"operationName": "getOrders", "response": "[{\"id\": \"O1\", \"status\": \"PENDING\"}, {\"id\": \"O2\", \"status\": \"SHIPPED\"}, {\"id\": \"O3\", \"status\": \"PENDING\"}]"}
  ],
  "expectedJson": "[{\"id\": \"O1\", \"status\": \"PENDING\"}, {\"id\": \"O3\", \"status\": \"PENDING\"}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQT0_CQBDFv8pmTpCQFq9NSFRiEA-agDeXw9od6Gq7u-xODaTpd3fKHwHx4qXJ7Lz-3rzXQMwLrBRkYCrvAglSG5MURD955M-Lx6DIOCvt1X6G6xoj3Tu9lbynrUcxriO5CsNUC2MLDIaimFMwdvVL8KwqvJZIWzmN5Y9KNNIKYXR2Bu5eLP-dXbCkbaWNGL5MfjKZH-YdxR2jiBXSUdE7cfsnYC9Jkj4TYQAcMWy5nduLNnoVUuG0GAkJk4dXCQNRh3I3psqbdJ3mB1RMG6NbCYzbocTkzPwyWH9_59JYLRrxx4ViNOIu-qLdn-ZV4ODEHpA1YDRfOR4Ob6AdQKT6nV_fGvhJ3ZXEirPsjAgYvePaeNFIRkjIOEJH6SJJ6Hrev92VXKSEzhfzwk2trwmypSojDiB3VpvORJWzA7FzX7QLVm885oT6KTr7L5tIKv-ccipblyWnDe6DQfux_QZVHYXKuAIAAA==)

```json
{
  "schema": "import taxi.http.HttpOperation\nimport taxi.http.RequestBody\n\ntype CustomerId inherits String\ntype CustomerName inherits String\n\nmodel Customer {\n  id: CustomerId\n  name: CustomerName\n}\nservice CustomerService {\n  operation getCustomer(CustomerId): Customer(...)\n}",
  "query": "@HttpOperation(method = \"GET\", url = \"/api/q/customers/{id}\")\nquery GetCustomer(id: CustomerId) {\n  find { Customer(CustomerId == id) }\n}",
  "parameters": {"id": "C001"},
  "stubs": [
    {"operationName": "getCustomer", "response": "{\"id\": \"C001\", \"name\": \"Alice\"}"}
  ],
  "expectedJson": "{\"id\": \"C001\", \"name\": \"Alice\"}"
}
```

---

### 9.2 Publish named queries as stream processors

**Impact: HIGH (enables real-time event processing with continuous data enrichment)**

Named queries using `stream {}` continuously process events as they arrive. They can be published as persistent stream processing pipelines by wrapping them in named queries and optionally annotating them with `@HttpOperation` to expose results as SSE streams.

IMPORTANT:
- Queries must be saved as a source file in the taxi project.
- They must be enabled once in the endpoints panel of Orbital before they are automatically enabled.
- Once they have been enabled once, they will stay activated until disabled, including during a restart

##### Basic stream query

```taxi
// Subscribe to all events of a type from available stream sources (Kafka, etc.)
query OrderEventProcessor {
    stream { OrderPlaced }
}
```

The engine looks for operations that return `Stream<OrderPlaced>` (e.g., a Kafka consumer) and processes each event.

##### Stream with projection and enrichment

Use `as {}[]` to transform and enrich each event:

```taxi
query EnrichedOrderStream {
    stream { OrderPlaced } as {
        orderId: OrderId
        customerName: CustomerName   // Enriched from CustomerService if available
        amount: OrderAmount
        region: DeliveryRegion
    }[]
}
```

The `[]` suffix is required for stream projections — it means "project each event individually".

##### Union streams

Use `|` to merge multiple event types into a single stream:

```taxi
query DeliveryEventStream {
    stream { FastShipDeliveryEvent | StandardShipDeliveryEvent } as {
        productId: ProductId
        quantity: DeliveredQuantity
        deliveryDate: DeliveryDate
    }[]
}
```

##### Expose stream as SSE endpoint

Combine with `@HttpOperation` to publish the stream as a Server-Sent Events (SSE) endpoint:

```taxi
import taxi.http.HttpOperation

@HttpOperation(url = "/api/q/orders/live", method = "GET")
query LiveOrderStream {
    stream { OrderPlaced } as {
        orderId: OrderId
        amount: OrderAmount
        status: OrderStatus
    }[]
}
```

Callers connect via `GET /api/q/orders/live` and receive a continuous SSE stream.

##### Stream + write (pipeline)

Use `call` to write enriched events to another system:

```taxi
query OrderToMongoProcessor {
    stream { OrderPlaced }
    call OrderStorageService::upsert
}
```

##### Key points

- `stream {}` continuously processes events; `find {}` queries once and returns
- The projection suffix `[]` is required: `as { ... }[]` — not `as { ... }`
- `|` merges multiple event types into one stream
- Enrichment via service calls works the same as in regular `find` queries
- Without `@HttpOperation`, the named query runs as a background pipeline

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/6VRwW7bMAz9FUGnFAic9LCLsQ0t2mDLNizFsluUg2oxsxZZUiW6aGH430dZTusA3Q6TAcEkn_gen9TxWNXQSF5y3XgXkKF80kWN6IvPtG08BInaWUEfPntgm6AgrBXTtoagMbItBm1_TatblNjGfyGuG9dafEXcQqUbaRJJ4xSYjLozsgLFOmEZ06o8UadQDg3KabeUjgNzOZUhbC9shPCoq5F89QgWt2NmaH6lXeHCvUZpiqM8HGXxNe0vw8_QeV2xD0xwlzpEwefMHQ4RcEiCDEZDRMEvUjt3Okd6AshmYI2zizI5QfH7yXQfSR-f84cWwjNdwtWZ6bMGsHZq4Pi0-plY22CGcCG9XjwsspxF5hnoh05sZYOmi1WjEamaR2WjJtadedwzGU-AtFy2-szzvP7ifF5v-p8K_W6fB_UySJqKRPOy6-c8YntPv7uOv7j2nQBkxdQ7OhggekcXSZVdJ7hWgpfkw-YyuSJ4lpVyl--Wy2KZcllNxt19u75Z3Qre76kXVLVbW98iLw_SRJjzylmlE7k0P0aipGrf7wn95KFCUF-is8TejY9g_b8KeJpaVse14qVtjSFTgvtNFDns_wA3zgOflAMAAA==)

```json
{
  "schema": "import taxi.http.HttpOperation\n\ntype OrderId inherits String\ntype OrderStatus inherits String\ntype OrderAmount inherits Decimal\n\nmodel OrderPlaced {\n  id: OrderId\n  amount: OrderAmount\n  status: OrderStatus\n}\nservice OrderEventService {\n  @io.orbital.kafka.KafkaOperation(topic = \"orders\", offset = \"earliest\")\n  operation streamOrders(): Stream<OrderPlaced>\n}",
  "query": "query OrderStream {\n    stream { OrderPlaced }\n}",
  "parameters": {},
  "stubs": [
    {"operationName": "streamOrders", "response": "[{\"id\": \"O1\", \"amount\": 100.0, \"status\": \"PLACED\"}]"}
  ],
  "expectedJson": "{\"id\": \"O1\", \"amount\": 100.0, \"status\": \"PLACED\"}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/51SwW7bMAz9FUGnFAic9LCLsQ0t2mDLNizFsluUg2oxsxZZUiW6aGH430dZTusA3Q6TAcEkn_gen9TxWNXQSF5y3XgXkKF80kWN6IvPtG08BInaWUEfPntgm6AgrBXTtoagMbItBm1_TatblNjGfyGuG9dafEXcQqUbaRJJ4xSYjLozsgLFOmEZ06o8UadQDg3KabeUjgNzOZUhbC9shPCoq5F89QgWt2NmaH6lXeHCvUZpiqM8HGXxNe0vw8_QeV2xD0xwlzpEwefMHQ4RcEiCDEZDRMEvUjt3Okd6AshmYI2zizI5QfH7yXQfSR-f84cWwjNdwtWZ6bMGsHZq4Pi0-plY22CGcCG9XjwsspxF5hnoh05sZYOmi1WjEamaR2WjJtadedwzGU-AtFy2-szzvP7ifF5v-p8K_W6fB_UySJqKRPOy6-c8YntPv7uOv7j2nQBkxdQ7OhggekcXSZVdJ7hWgpfkw-YyuSJ4lpVyl--Wy2KZcllNxt19u75Z3Qre76kXVLVbW98iLw_SRJjzylmlE7k0P0aipGrf7wn95KFCUF-is8TejY9g_b8KeJpaVse14qVtjSFTgvtNFDns_wA3zgOflAMAAA==)

```json
{
  "schema": "import taxi.http.HttpOperation\n\ntype OrderId inherits String\ntype OrderStatus inherits String\ntype OrderAmount inherits Decimal\n\nmodel OrderPlaced {\n  id: OrderId\n  amount: OrderAmount\n  status: OrderStatus\n}\nservice OrderEventService {\n  @io.orbital.kafka.KafkaOperation(topic = \"orders\", offset = \"earliest\")\n  operation streamOrders(): Stream<OrderPlaced>\n}",
  "query": "@HttpOperation(method = \"GET\", url = \"/api/q/orders/stream\")\nquery EnrichedOrderStream {\n    stream { OrderPlaced } as {\n        orderId: OrderId\n        amount: OrderAmount\n        status: OrderStatus\n    }[]\n}",
  "parameters": {},
  "stubs": [
    {"operationName": "streamOrders", "response": "[{\"id\": \"O1\", \"amount\": 1500.0, \"status\": \"PLACED\"}]"}
  ],
  "expectedJson": "{\"orderId\": \"O1\", \"amount\": 1500.0, \"status\": \"PLACED\"}"
}
```

---

## 10. Query Control

**Impact: LOW-MEDIUM**

Fine-grained control over which services and operations the query engine considers. `using {}` restricts to a specific set; `excluding {}` removes services from consideration. Also covers `@Cache` for cross-query caching of reference data, cache naming, TTL, and disabling caching on specific operations or models.

---

### 10.1 Use @Cache for cross-query caching

**Impact: MEDIUM (prevents redundant service calls across multiple query executions)**

By default, Orbital caches service calls **within a single query**. If the same operation is called with the same parameters more than once during a query (e.g., when enriching multiple rows), the second call uses the cached result.

Adding `@Cache` enables **cross-query caching** — responses survive beyond the current query and are reused by subsequent queries. This is useful for reference data that changes infrequently (customer profiles, product catalogs, exchange rates).

##### Default per-query caching (always on)

Without any annotation, Orbital automatically caches each operation call for the lifespan of the current query. The same operation with the same parameters will never be called twice within a single query execution.

```taxi
// No annotation needed — per-query caching is automatic
find { Order[] } as {
    orderId: OrderId
    customerName: CustomerName   // getCustomer(CustomerId) called once per unique CustomerId
}[]
```

##### @Cache: cross-query caching

Add `@Cache` to persist the cache beyond the current query:

```taxi
@Cache
find { Order[] } as {
    orderId: OrderId
    customerName: CustomerName   // Results survive across query executions
    customerTier: CustomerTier
}[]
```

##### Named caches for isolation

By default, all queries share the same cache. Use a name to isolate caches so population and invalidation are independent:

```taxi
@Cache("customerCache")
find { Order[] } as {
    orderId: OrderId
    customerName: CustomerName
}[]
```

##### Disabling caching on an operation or model

Use `CachePolicy.Disabled` to prevent caching of specific operations or models:

```taxi
import com.orbitalhq.caching.Cache

service PersonApi {
    // Responses from this operation are never cached
    @Cache(mode = CachePolicy.Disabled)
    operation getAccount(AccountId): AccountState
}
```

Or on a model, to prevent caching of all responses of that type:

```taxi
import com.orbitalhq.caching.Cache

@Cache(mode = CachePolicy.Disabled)
model AccountState {
    // ...
}
```

##### Cache TTL

The default TTL is 5 minutes. Override with `maxIdleSeconds`:

```taxi
import com.orbitalhq.caching.Cache

service PersonApi {
    // Evicted after 30 seconds of inactivity
    @Cache(maxIdleSeconds = 30)
    operation getAccount(AccountId): AccountState
}
```

##### External caches

For production use, configure an external cache (Hazelcast or Redis) and reference it by connection name:

```taxi
@Cache(connection = "myHazelcast")
find { Film[] } as {
    id: FilmId
    currentReviewScore: ReviewScore
}
```

##### Streams are never cached

Operations returning streams are excluded from caching.

##### Key points

- **Per-query caching is always on** — no annotation needed, same call with same parameters never executes twice in one query
- **`@Cache` enables cross-query persistence** — cache survives after the query completes
- **Named caches isolate populations** — useful when different queries manage different data independently
- **`CachePolicy.Disabled` prevents any caching** — use for frequently-changing data

Reference: [Orbital Caching Docs](https://docs.orbitalhq.com/docs/querying/caching)

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/61SPU_DMBD9K5anVgoDdMtU1AGFoSDaLclgnGtrSOxgOwgU5b9zrpOmoSnia7uvd_fe3dXU8B0UjIa0UBnk5E5noEmdSEKUM6Ms9LEoI0LuQAtryMpqIbeuhlfGqsKXLQ72aWWTSN-_K7rXaiNy8JPmCBnv5LKSFdDHluiNMbECdF-1Rm-UhQH9Kjh4TavW8XJL0MwKJckW7D5tJtNWfJwOwN2U8_iuYtJLmYafxWNPGtCXCvQ77n--YHiKRG6EzEjdzSUNYcYPOL2Ij_KjzQz3NMyvTzaEBOIUKZRMY7VFxTSsm4AaWz2iGdf0IMq1Q5KH1SBKgykV7gTDcZ3QllxCQ7J3Li4TGqDZH9annO9yTUDGUFe_Qs2-QGHHxqkEvlORLCtLww3LDQSUK5kJp47lD60YJzttglHl3eqG2uvzZB0l970-es3xcxdKlz7hHtYnblSOwOZnFFOsfiuBW8hujZLfPMLyHJvjJxmw-taV_qvt7K9t3Z2NZfw5ymgoqzzH59bqCdfk3eYD11ZsVvEEAAA=)

```json
{
  "schema": "model Order {\n  orderId: OrderId inherits String\n  customerId: CustomerId inherits String\n}\nmodel CustomerProfile {\n  @Id customerId: CustomerId\n  name: CustomerName inherits String\n  tier: CustomerTier inherits String\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}\nservice CustomerService {\n  operation getCustomer(CustomerId): CustomerProfile\n}",
  "query": "@Cache\nfind { Order[] } as {\n    orderId: OrderId\n    customerName: CustomerName\n    customerTier: CustomerTier\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getOrders",
      "response": "[{\"orderId\": \"ord-1\", \"customerId\": \"cust-1\"}, {\"orderId\": \"ord-2\", \"customerId\": \"cust-1\"}, {\"orderId\": \"ord-3\", \"customerId\": \"cust-2\"}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getCustomer",
      "response": "{\"customerId\": \"cust-1\", \"name\": \"Acme Corp\", \"tier\": \"Gold\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"orderId\": \"ord-1\", \"customerName\": \"Acme Corp\", \"customerTier\": \"Gold\"}, {\"orderId\": \"ord-2\", \"customerName\": \"Acme Corp\", \"customerTier\": \"Gold\"}, {\"orderId\": \"ord-3\", \"customerName\": \"Acme Corp\", \"customerTier\": \"Gold\"}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/7WQwU7EIBCGX2UyJ0160SMnExOTmujB3VvpAWG6RSlUoGZN03d3aFezcc-emIGPfz6YMemeBoUCh2DIwYN1A8zSA1gj1q42YH1P0eYEuxytP5TTbLOjDdiX8pJZpE8UP62mldqd6jU7jBRVtsHDgXI5TVfXW1jT8k2s8GOi-MVWd_eKBaXvrDcwnxBYQKUt6dxz6_-acV7TcuKoohooU0wo5qXClKdXLpsZf22eGeCZP058KVIaA7-Dd5tZojUSBUjsbiRWvK6ztq3aaxpLiMSlgnP29oLd9wRPiv_pyHBxI92H2o9TRtEpl6hCHbyxJU65l5NDkW2XlunjSDqTeUzB_7NYykq_1waFn5zjP4zhjUdv7fIN0_ecrj0CAAA=)

```json
{
  "schema": "model Film {\n  id: FilmId inherits String\n  title: FilmTitle inherits String\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}",
  "query": "@Cache(\"filmCache\")\nfind { Film[] } as {\n    id: FilmId\n    title: FilmTitle\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFilms",
      "response": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]"
}
```

---

### 10.2 Control which services the query engine uses

**Impact: MEDIUM (enables precise control over data sources for performance and correctness)**

Use `using` and `excluding` clauses to control which services and operations the query engine considers when resolving data.

##### Include specific services

```taxi
// Only use specific services/operations
find { Film[] }
using {
    FilmService::getFilms,    // Specific operation
    ReviewService             // Entire service
}
```

##### Exclude specific services

```taxi
// Exclude specific services/operations
find { Film[] }
excluding {
    ImdbApi,                      // Exclude entire service
    RottenTomatoes::getReviews    // Exclude specific operation
}
```

##### When to use

- **`using`**: When you know exactly which services should provide data. Useful for performance or when multiple services could respond.
- **`excluding`**: When you want most services but need to skip specific ones (e.g., a slow or deprecated service).

##### Syntax notes

- `using` and `excluding` go **after** the main query, outside the `find {}` block
- You can reference entire services (`ServiceName`) or specific operations (`ServiceName::operationName`)
- Multiple entries are comma-separated

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/7VQvU7DMBB-FesmkLLA6AdAChIMbbc4g7GvjcGxXdspRVHenUucoqKKkcn38_v5Rkiqw14Ch95rtOzJ2J6NwjFmNF-6WjPjOowmJ7bN0bjDvM0mWyyA3VzeYibhEsaTUbigtmu9aPuAUWbjHTtgnrfp7r6INe0v5gZPBj__5pb9wi7lwocKjgPGL_rV3jjNxlWakfKQKF1RuorF-SVIoQcZZY8ZYwI-ThWkPLxR2YzwY_9KADK48IgUMQVP0WnajAKMFsCZgP2DgIre5WRlVDuFYRYRMFXsGvt4g911yF4kHfVM4JZsUHW-dmHIwPfSJqxAeafNLCftZs0wh22nltDngCqjfk7e_XOwlKX6qDVwN1hLN4z-naxLO30DW6mX0moCAAA=)

```json
{
  "schema": "model Film {\n  id: FilmId inherits String\n  title: FilmTitle inherits String\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}\nservice ReviewService {\n  operation getReviews(): Review[]\n}",
  "query": "find { Film[] }\nusing {\n  FilmService::getFilms\n}",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFilms",
      "response": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/7WQQU_EIBCF_wtDTptedNmbF5Oa6MHdW-mBhekWpQBBTQ3pf3daKlH37IkBeO-bx2SIcsBRQA2jU2jYvTYjy9wyplW9XRvFtB0w6BTZIQVtz-s26WSwAMf1vGQWbiOGdy1xow57vXk7j0GkbSw7Y1q78eq6mLXdL2UzqtOd139VEUWQw6UQKnibMHzQd3ptFct7i5ElztJMikKUrpvtzkXkRRAjJgwR6rxUENJ0orLN8D37iQCy_cpLooA9OktKr23moBWHmnnobThUdG4bKk-Nlehcewsr2K7rnepXU2_spnYv_Eq9e7XbVMgea8dUYw25ZQqfbkOfsBs5YfHbQfZO92Q60mKKVyolcVqhYM4n3e_BWVN67WJzV3PaHQcBSRz_q5KPnPdmTYoBkWu_FJjZCRBaHl4jLGtX8AQAA)

```json
{
  "schema": "model Film {\n  id: FilmId inherits String\n  title: FilmTitle inherits String\n}\nservice FilmService {\n  operation getFilms(): Film[]\n}\nservice ImdbApi {\n  operation searchFilms(): Film[]\n}",
  "query": "find { Film[] }\nexcluding {\n  ImdbApi\n}",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getFilms",
      "response": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "[{\"id\": \"f1\", \"title\": \"Inception\"}, {\"id\": \"f2\", \"title\": \"The Matrix\"}]"
}
```

---

## 11. Patterns

**Impact: MEDIUM**

Common end-to-end patterns that combine multiple features. Includes numeric aggregation (`sum`, `min`, `max` across related collections), dynamic categorisation with `when`, and nested projections with service enrichment. Use these as reference when a query needs to combine features from multiple sections.

---

### 11.1 Common aggregation and summarization patterns

**Impact: MEDIUM (enables business-logic aggregations like totals, extremes, and categorization)**

These patterns combine collection functions, expressions, and `when` blocks to compute summaries and derived fields.

##### Numeric aggregation: sum, min, max

Compute totals and statistics using collection aggregation functions:

```taxi
find { Order[] } as {
    orderId: OrderId
    totalSpent: Decimal = OrderItem[].sum((UnitPrice) -> UnitPrice)
    cheapestItem: Decimal = OrderItem[].min((UnitPrice) -> UnitPrice)
    mostExpensive: Decimal = OrderItem[].max((UnitPrice) -> UnitPrice)
}[]
```

**Note:** The callback in `sum`, `min`, and `max` receives each element of the collection. Use type-based callbacks when the type is unambiguous:

```taxi
// ✅ Preferred: type-based — TaxiQL finds the right field
Transaction[].sum((TransactionAmount) -> TransactionAmount)

// ⚠️ Less preferred: field-based — coupled to field name
Transaction[].sum((t:Transaction) -> t.amount)
```

##### Dynamic categorization with `when`

Classify data into categories based on runtime values:

```taxi
find { Customer[] } as {
    name: CustomerName
    segment: String = when {
        LifetimeValue > 10000 && DaysSinceLastOrder < 30 -> "VIP Active"
        LifetimeValue > 10000                            -> "VIP Dormant"
        DaysSinceLastOrder < 90                          -> "Regular Active"
        else                                             -> "At Risk"
    }
}[]
```

Always include an `else` branch to handle unmatched cases.

##### Inline derived fields

Combine source values to compute new fields:

```taxi
find { OrderItem[] } as {
    lineTotal: Decimal = UnitPrice * Quantity
    discounted: Decimal = (UnitPrice * Quantity) * (1 - DiscountRate)
    displayPrice: Decimal = UnitPrice.round(2)
}[]
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4VRTU_DMAz9K5ZPm1SmAYJDJZCQkBAcEGIHDssOWWu6QJOUfMBQ1f9O0rB1AjZycmw_Pz-_Fm2xIskxR6lLqmHGa4KWKYDG6NIXLoeHFNxzSSDUioxwFmbOCFXFPi61V6EtIq_6eOi6pkJIXjPVYYZvnsxnIKrEO6nEAT1qvoALmKd_fO3AzfBJlBU5htmW6PhsOplCl-0B3PCfgNPpQcBvhpMESP2LsD1Tz0KVm52ddrx-pKDCU77RGCQkLRPr5Wg0XGMMR5c7xxmnGUuyLib_wEu-_h__oc3-AUIdHhDdaLgJhjoyFvO2y9A6vwzhfJEhrRsqHJV3VqvgV6-a4a5qhjmcRxuyVNuoifn-2t_57Zax0PuWyK3jxettibnydR12MfolMKZv9wUmqySblAIAAA==)

```json
{
  "schema": "model Sale {\n  product: ProductName inherits String\n  amount: SaleAmount inherits Decimal\n}",
  "query": "given {\n    Sale[] = [\n        { product: \"Widget\", amount: 150.0 },\n        { product: \"Gadget\", amount: 300.0 },\n        { product: \"Widget\", amount: 200.0 }\n    ]\n}\nfind {\n    totalRevenue: Decimal = Sale[].sum((SaleAmount) -> SaleAmount)\n    bestSale: Decimal = Sale[].max((SaleAmount) -> SaleAmount)\n    worstSale: Decimal = Sale[].min((SaleAmount) -> SaleAmount)\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"totalRevenue\": 650.0,\n  \"bestSale\": 300.0,\n  \"worstSale\": 150.0\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5VSy07DMBD8lZUPnAJKgSKIoFKhlyIECKRekh7cZNsaHAdsh4ei_DsbkqZpcUH4FG92xrOzUzATLzHlLGBplqCEq9zYLEUNRaQAFE8xaGu3dAOhlqiFNfBotVCLqkuKOVqR4oTLnNpvutd1_whjkXJZARL-aR6FivGGG3unE9QBjH7U1tCxspEqmcdec9SfpHUh3lDVEqGVF07hAsK6Vp2ikR-xoRQxRszbVtrr-75_4HtOQT0fSs_FdpnN_st1sovrasm1FC5t_d1sx_0dbCPBFXdwHf4y5SlJq7mm5HGk5kIlRNkxtQRuVl7_DERdN7hIUdmgSQUt4n253lB1NlMxIHvpwN6ea_HncOTD_oAmmozvYRhbWnfE_uJqAaNMp5wi00E4HzlrMA-4yCXXjodQGqx7hhYehHle_SSjynBKiXzhmkywqA0LitJjxuYz-gynHsOPF4wtJtcmU5TZ72QWRFV5GLGNXEasMbCud6duVr0JbCLogLWzO3GduG1htz1wwtt8bYFbcypfKleM5fHzOGGByqUkk3T2RFbU1_ILMQXKz3QEAAA=)

```json
{
  "schema": "model Customer {\n  name: CustomerName inherits String\n  lifetimeValue: LifetimeValue inherits Decimal\n  daysSinceLastOrder: DaysSinceLastOrder inherits Int\n}",
  "query": "given {\n    Customer[] = [\n        { name: \"Alice\", lifetimeValue: 15000.0, daysSinceLastOrder: 10 },\n        { name: \"Bob\", lifetimeValue: 15000.0, daysSinceLastOrder: 60 },\n        { name: \"Charlie\", lifetimeValue: 500.0, daysSinceLastOrder: 45 },\n        { name: \"Diana\", lifetimeValue: 200.0, daysSinceLastOrder: 180 }\n    ]\n}\nfind { Customer[] } as {\n    name: CustomerName\n    segment: String = when {\n        LifetimeValue > 10000 && DaysSinceLastOrder < 30 -> \"VIP Active\"\n        LifetimeValue > 10000 -> \"VIP Dormant\"\n        DaysSinceLastOrder < 90 -> \"Regular Active\"\n        else -> \"At Risk\"\n    }\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n  { \"name\": \"Alice\", \"segment\": \"VIP Active\" },\n  { \"name\": \"Bob\", \"segment\": \"VIP Dormant\" },\n  { \"name\": \"Charlie\", \"segment\": \"Regular Active\" },\n  { \"name\": \"Diana\", \"segment\": \"At Risk\" }\n]"
}
```

---

### 11.2 Nested projection with enrichment from services

**Impact: HIGH (enables hierarchical data composition where nested items are enriched from separate services)**

When projecting nested collections, each nested item can also trigger discovery — TaxiQL automatically calls services to populate missing fields at every level of nesting.

##### How nested enrichment works

```taxi
find { Invoice[] } as {
    invoiceNumber: InvoiceNumber
    items: InvoiceItem[] as {
        // These fields are not on InvoiceItem — TaxiQL discovers them
        // via ProductId → ProductService.getProduct
        productName: ProductName     // Discovered from ProductService
        category: ProductCategory    // Discovered from ProductService
        quantity: Quantity           // From InvoiceItem directly
        lineTotal: Decimal = UnitPrice * Quantity  // Computed inline
    }[]
}[]
```

**What happens:**
1. `find { Invoice[] }` — fetches all invoices from `InvoiceService`
2. `items: InvoiceItem[] as {...}[]` — iterates over each item in `invoice.items`
3. For each `InvoiceItem`, TaxiQL finds `ProductName` and `ProductCategory` using the `ProductId` key
4. Each nested item triggers a separate service call if needed

##### Key principle

Each nested projection scope has its own discovery context. The data visible to the nested `as {}` block is:
- The current nested object (e.g., `InvoiceItem`)
- Any data already in scope from the outer projection (e.g., `Invoice`)
- Results from any service calls triggered by the available keys

##### Nested scope with outer data access

Use named scopes to access outer-level data from within a nested projection:

```taxi
find { Order[] } as (order: Order) -> {
    orderId: OrderId
    items: OrderItem[] as (item: OrderItem) -> {
        productName: ProductName
        quantity: Quantity
        // 'order' from the outer scope is available here
        orderDate: OrderDate = order::OrderDate
    }[]
}[]
```

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/51SwW7UMBD9FcunFqVVQOrFEhJSeyAc2qUFcdjkYOzpriGxU9sBVlH-HU_sJFs2PRSfZp7fjN-8cU-d2EPDKaONkVCTOyvBkr7UhBgMC8kiVkii9B6s8o48eKv0DjnKQ-MmRoi3VamHUh_1QjT2a62RnfDYcTOFaz2fOq698gdGPqdoYRXaI6XTym-sEsDI1ylcSDcgVMPrUYmojQNJoqD0bJTzAV8_1oKg5g3M0G1I1gQK7mFn7GEmXifglBwUOLC_UN7oxkNKosEtWO6V0WQHfrx2Z-fJzGTkVJweerk8Ec7mac5ndaERzehTB_YQ1vyotCT99AgZCHex3enCI3q64qMSPO3i1jPrFsaLhi2U06Uvd7XS8MV4XrNpteT90d7f_FMyjN5tqzB0y20Q4oOxlPVDRp3vvodw29PZPFQabJk3EKosuNYE6wO87UuabCkpIyW9u7-5yPO3Jc1CMlqDONLm7x2JmF4k3jQc3rxDYP6_I3J1medDNaBgEHtT6LbzlD3y2kFGhdFSoVBe3yddOEE1ZKtDJIOfTxHUqRVZ-NkjurHQqK4h35QMPeLttLTI-Mit_M1t4A-vk1kF9p8WhAf5yRkd1bzO0tv_0rlq_PyVELnKL9F3iv-Ci5-FpEx3dZ3huz-C4pgOfwG6WXBcJAUAAA==)

```json
{
  "schema": "model Order {\n  orderId: OrderId inherits String\n  items: OrderItem[]\n}\nmodel OrderItem {\n  productId: ProductId inherits String\n  quantity: Quantity inherits Int\n  unitPrice: UnitPrice inherits Decimal\n}\nclosed model Product {\n  @Id id: ProductId\n  name: ProductName inherits String\n  category: ProductCategory inherits String\n}\nservice OrderService {\n  operation getOrders(): Order[]\n}\nservice ProductService {\n  operation getProduct(ProductId): Product\n}",
  "query": "find { Order[] } as {\n    orderId: OrderId\n    items: OrderItem[] as {\n        productName: ProductName\n        category: ProductCategory\n        quantity: Quantity\n        lineTotal: Decimal = UnitPrice * Quantity\n    }[]\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getOrders",
      "response": "[{\"orderId\": \"ORD-001\", \"items\": [{\"productId\": \"prod-1\", \"quantity\": 2, \"unitPrice\": 25.00}]}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getProduct",
      "response": "{\"id\": \"prod-1\", \"name\": \"Premium Widget\", \"category\": \"Hardware\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "{\"orderId\": \"ORD-001\", \"items\": [{\"productName\": \"Premium Widget\", \"category\": \"Hardware\", \"quantity\": 2, \"lineTotal\": 50.0}]}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/6VTTY_TMBD9K5ZPLcquWhAcIoGQ4EA4VAvLx6HJwZt4W0NiZ21noYry35mpHSdtAghy8sy8eX7zxmmpyY-8YjSmlSp4SRL5qETOSZtKQoQLdk11x3Xc11wIxSPXwhpya7WQhzPe8soEXALRPktll8oJN-Ydf61V0eQ2KWJy0x-XeB8aJq2wp5h88KcBlUiLkEYKe6OBPSaf--MAestzUbFypMXf53S8xmvHIjApWcVDagfBkrKcWX5Q-hSAb3xiDoarDdePqMsbcevDswZVc82sUJIcuPUAs1oHN72TPYW_7vcUHrAKM62DRiCiEX1ouD7B3u-FLEg7XEM6wowj_PML8IiFnY8I8KsHDyeGDoj5godaKST_pCwr436N5OVox08uWjo0aiR91jpRem2aarUKbGty9WrgXoNR-wysqpkGuZZrQ-O2i6ixzR0c9y0NluM8YOZod9CnuakVrAwK-jalEzNTGpOUJrsvV5vNNqURBGcrMY_g8G84YO0xvVOYfYaJ8PAxs91cb7qILLQ_nbdvZ-3PsT3rcGSeH1Ui68bS-J6Vhkc0V7IQOCorP_q50IOsixZt8HueuoAmXAyEv5nLfBUF9Lls_1-5yjumix9MA677N2kZoH_WPLe8eG-U9Ar-Yw27RZWzZYS3es5cLuPvJNsZiVvJWdboPWPlBVYoPkaWf08KGsumLCO86xsM7MLuF1QuIVPgBQAA)

```json
{
  "schema": "model Invoice {\n  invoiceNumber: InvoiceNumber inherits String\n  items: InvoiceItem[]\n}\nmodel InvoiceItem {\n  productId: ProductId inherits String\n  quantity: Quantity inherits Int\n  unitPrice: UnitPrice inherits Decimal\n}\nmodel Product {\n  @Id id: ProductId\n  name: ProductName inherits String\n  category: ProductCategory inherits String\n}\nservice InvoiceService {\n  operation getInvoices(): Invoice[]\n}\nservice ProductService {\n  operation getProduct(ProductId): Product\n}",
  "query": "find { Invoice[] } as {\n    invoiceNumber: InvoiceNumber\n    items: InvoiceItem[] as {\n        productName: ProductName\n        quantity: Quantity\n        lineTotal: Decimal = UnitPrice * Quantity\n    }[]\n    invoiceTotal: Decimal = InvoiceItem[].sum((UnitPrice) -> UnitPrice)\n}[]",
  "parameters": {},
  "stubs": [
    {
      "operationName": "getInvoices",
      "response": "[{\"invoiceNumber\": \"INV-001\", \"items\": [{\"productId\": \"p1\", \"quantity\": 3, \"unitPrice\": 10.0}, {\"productId\": \"p2\", \"quantity\": 1, \"unitPrice\": 50.0}]}]",
      "echoInput": false,
      "conditionalResponses": []
    },
    {
      "operationName": "getProduct",
      "response": "{\"id\": \"p1\", \"name\": \"Widget\", \"category\": \"Hardware\"}",
      "echoInput": false,
      "conditionalResponses": []
    }
  ],
  "expectedJson": "{\"invoiceNumber\": \"INV-001\", \"items\": [{\"productName\": \"Widget\", \"quantity\": 3, \"lineTotal\": 30.0}, {\"productName\": \"Widget\", \"quantity\": 1, \"lineTotal\": 50.0}], \"invoiceTotal\": 60.0}"
}
```

---

## 12. Advanced Features

**Impact: LOW**

Less frequently needed capabilities. User-defined (composed) functions in the schema for reusable transformation logic, and throwing errors using TaxiQL's casting syntax (`throw((ErrorType) { ... })`).

---

### 12.1 Throw errors using casting syntax

**Impact: LOW (enables validation and error handling in queries and functions)**

TaxiQL uses a casting syntax for errors since Taxi has no constructors. Errors are fatal — there is no catch mechanism.

##### Defining error models

```taxi
import com.orbitalhq.errors.Error

@ResponseCode(403)
model ForbiddenError inherits Error {
    message: ErrorMessage
}
```

##### Throwing errors

```taxi
// Throw an error using casting syntax
throw((ForbiddenError) { message: "Access denied" })
```

##### In validation functions

```taxi
function validateAge(age: Age): Age ->
    when {
        age < 18 -> throw("Age must be 18 or over")
        else -> age
    }
```

##### Custom response bodies

```taxi
import taxi.http.ResponseBody

model BadRequestError inherits Error {
    @ResponseBody
    error: {
        errorCode: String
        message: String
    }
}

throw((BadRequestError) {
    error: { errorCode: 'E1234', message: "Invalid input" }
})
```

##### Validation

Note: When a throw is actually executed, the query terminates with an error (HTTP 500). These examples validate the throw syntax is correct by demonstrating the non-throwing branch executes successfully.

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22PT0vEMBDFv8owpy3UZf1zkKKiiMIKXtSb8ZBtxzbaTrqT1HUp_e4mjSiCp8zLvPfyy4iubKjTWKDpeiseStstrWyM122zXZKIFbe8iYdixZcP5HrLjq5tRYuT1XGmuAtjC7cxVFXEsxcMNyTGO0hyVAzQkXO6piLd3Sf163z0YrhWPGGO24FkH6Bq80EMI8yxNXs4h6MVTIpfDVepVcgNrS--48Gwa2IkriDm4AwOT-HgAnwjdrdY_AXNQvkPl8Ina2Fvh4CBMGWpg1pHMa_wqiyDFWrR7KlSGPdTAu616I48icNinHJ0ftiE8fklR_rsqQz-O2c5fGkmU5iwFRb_9sZK53X5vq6w4KFtwwti30JPktMX1RNTHrkBAAA=)

```json
{
  "schema": "import com.orbitalhq.errors.Error\n\n@ResponseCode(403)\nmodel ForbiddenError inherits Error {\n  message: ErrorMessage inherits String\n}",
  "query": "given { age: Int = 20 }\nfind {\n  result: String = when {\n    age < 18 -> throw((ForbiddenError) { message: \"Too young\" })\n    else -> \"Access granted\"\n  }\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"result\": \"Access granted\"\n}"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2WQwU7DMAyGXyXyqZNKVSROlYYQaIchIaFxJByyxKyB1umSdANVfXeSZqMTnGq3nz_77wBO1tgKqEC3nbGeSdMWxm61F029L9BaY12xig9OnO426DpDDh-MwuymLBec2lA27F6oDe57dH6CmaYarfaOpXbgxNhki5NVehvLGXzxVtMuci06J3Zn6il1_8ERcggb7Xe4fqcPSGwIUNf76oSwJeNwEI1WHNjI6V2TSpd01sigRXWBHutoiF9Z0rBlnNd0NlzdMl9bc8yyP2EXYfFFNg6rsrzmkM9BOKyTJpnjOYu0CRuH0SwNSeEzDs_zaVEx8RM7psSdsKJFj9ZBNYw5ON9vQ_n6lgN-dSg9qkdnKPyTKQuH36wcovLCfwqWvM4L-blWUFHfNHmc-giy1I4_woPm9SgCAAA=)

```json
{
  "schema": "import com.orbitalhq.errors.Error\n\n@ResponseCode(400)\nmodel BadRequestError inherits Error {\n  errorCode: ErrorCode inherits String\n  message: ErrorMessage inherits String\n}",
  "query": "given { input: String = \"valid\" }\nfind {\n  processed: String = when {\n    input == \"invalid\" -> throw((BadRequestError) { errorCode: \"E001\", message: \"Invalid input\" })\n    else -> concat(\"Processed: \", input)\n  }\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\n  \"processed\": \"Processed: valid\"\n}"
}
```

---

### 12.2 User-defined and composed functions in TaxiQL

**Impact: LOW (enables reusable transformation logic defined in the schema)**

TaxiQL supports defining functions in the schema with a body — these are called "composed functions". They wrap common transformation logic and can be called in queries.

##### Defining composed functions

```taxi
// Define in schema
function formatDisplayName(first: FirstName, last: LastName): String ->
    concat(last, ', ', first).upperCase()

// Parameters must use named types (not raw String/Int)
function applyDiscount(price: Price, rate: DiscountRate): Decimal ->
    price * (1 - rate)
```

##### Calling functions in queries

```taxi
find { Person[] } as {
    display: String = formatDisplayName(FirstName, LastName)
    initials: String = concat(FirstName.left(1), '.', LastName.left(1), '.')
}[]
```

Parameters are resolved by type from the current scope — the engine finds `FirstName` and `LastName` from the current `Person` object.

##### Using stdlib functions

Standard library functions are called as regular function calls:

```taxi
find { Customer[] } as {
    upperName: String = upperCase(CustomerName)
    trimmedEmail: String = lowerCase(trim(EmailAddress))
    fullName: String = concat(FirstName, ' ', LastName)
}[]
```

**Note:** String functions use regular call syntax (`upperCase(x)`), not dot syntax (`x.upperCase()`). The dot syntax form is not supported. See `problems/string-extension-syntax.md`.

##### Validation

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQ30rDMBTGX-WQqw2iD1CYIG6yDd2Eedf0Irana7RNapKKo_TdTdI_G-oKgebkfL9zvq8lJi2w4iQi9lQjPApt7I5XCEIWqIU1cLBayCOT4f2JX3tmslIZlvCC2igJLZMA-UiLzmBfL_lYHnlMdh6RNzK1wslzpStul8LUJT_5hllgXXBooJwJ82hYBW7u_AyAVMmU25lvo8BIfwJmftvUNeoHbnA2J5R8NqhPLoKj-MJhdRiMxAksIO4r_msvTTGyVYX02LMjRpYKGYGOXhdxib9Fh0rYwst6VRICyYXMnHjapANuxvWyPpvJ9eKfzC7CmmJy4Dhxnmuu3dU6NInajhJjmzf3GyeU4HeNqcVs66a6VIL71u04jGQkuNyvKGz3693k9U_L4XnzunZN97tV78zPNZanH5uMRLIpS7eGVu9uWH_tfgBfx8NDjwIAAA==)

```json
{
  "schema": "type FirstName inherits String\ntype LastName inherits String\n\nmodel Person {\n  firstName: FirstName\n  lastName: LastName\n}\n\nfunction formatDisplayName(first: FirstName, last: LastName): String ->\n    concat(last, \", \", first).upperCase()",
  "query": "given {\n    Person[] = [\n        { firstName: \"John\", lastName: \"Doe\" },\n        { firstName: \"Jane\", lastName: \"Smith\" }\n    ]\n}\nfind { Person[] } as {\n    display: String = formatDisplayName(FirstName, LastName)\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n  { \"display\": \"DOE, JOHN\" },\n  { \"display\": \"SMITH, JANE\" }\n]"
}
```

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQXW-CMBSG_8pJr2ZC_AEkS1RCMo0fS9jFEspFhTOtoy1ry6Yh_Pe1oESn40qO530479MQk-9RMBISe6oQotpYJVCvmUDgco-aWwOJ1VzuqOxWYsF4OS0Kjcbcr1ApVIHlAIKGSgDpcOEN3E_Rk8IbIJUtCchXjfrkTtrxb5Q9AYZ0msEzpP3MP82ZTsm05DlCIrjdUxJc8JQAMP_PBI9MVCWOcyXAj9vgEWWrtnBQEs0NY7aZTeL36ep1GY-jzcqn-3DmTqbyg8vCMa5ubIGZy-l1VfWtw7Mn16CbRczg07WWUR9wS0JgEfdfHzKl-jln_MLTtbiRS7Zp5uRVTDuQRW1I2LQBMbbeup9pFhA8VphbLBZGSae3k9i4csN9lHQel_MohmQ1f3vxDii5PqdfuRM66HzAc-5gsVnHyX80p_wvi0rfxViWf84LEsq6LF01rQ6uQP_a_gJjP5EtvAIAAA==)

```json
{
  "schema": "type CustomerName inherits String\ntype EmailAddress inherits String\n\nmodel Customer {\n  name: CustomerName\n  email: EmailAddress\n}",
  "query": "given {\n    Customer[] = [\n        { name: \"Alice Smith\", email: \"  alice@example.com  \" },\n        { name: \"bob jones\", email: \"BOB@EXAMPLE.COM\" }\n    ]\n}\nfind { Customer[] } as {\n    upperName: String = upperCase(CustomerName)\n    trimmedEmail: String = lowerCase(trim(EmailAddress))\n}[]",
  "parameters": {},
  "stubs": [],
  "expectedJson": "[\n  { \"upperName\": \"ALICE SMITH\", \"trimmedEmail\": \"alice@example.com\" },\n  { \"upperName\": \"BOB JONES\", \"trimmedEmail\": \"bob@example.com\" }\n]"
}
```

---

## 13. Troubleshooting

**Impact: HIGH**

Diagnosing unexpected `null` values — the most common symptom of a query problem. Covers the three root causes: ambiguous `::` traversal (multiple matches), missing service operations or stubs, and values excluded from projection scope. Includes a diagnostic checklist.

---

### 13.1 Troubleshoot unexpected null values in query output

**Impact: HIGH (null values are one of the most common issues — understanding the causes allows fast diagnosis and fixes)**

Unexpected nulls in TaxiQL query output typically fall into three categories:

1. **Ambiguous type traversal** — `::` matches more than one field
2. **Type not reachable** — no service can provide the requested type, or the service contract doesn't expose it
3. **Values excluded from scope** — a scope modification removed data that the projection needs

---

##### Cause 1: Ambiguous type traversal

When `::` traversal finds more than one field of the requested type, the result is `null` rather than an error.

**Example — ambiguous traversal:**

```taxi
model FlightBooking {
    passengers: Passenger[]
}
model Passenger {
    name: PersonName inherits String
}
```

```taxi
given {
    flight: FlightBooking = {
        passengers: [
            { name: "Jim" },
            { name: "Jill" }
        ]
    }
}
find {
    passengerName: PersonName   // ❌ null — multiple passengers each have PersonName
}
```

**Fixes:**

```taxi
find {
    // ✅ Return all as an array
    allNames: PersonName[]

    // ✅ Navigate by index to access a specific item
    firstName: flight.passengers.getAtIndex(0)::PersonName
}
```

**Diagnosis:** If a field returns null but the data is definitely present, check whether the type appears in multiple places in the model. Use `TypeName[]` to confirm — if you get results as an array, ambiguity was the cause.

[Reproducer](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VQwU7DMAz9lcjnfEEkLhyQ2GFC4tj0EFqvDUvdLkkRKMq/47TrBkX4ZPv5+fk5QWh6HAwoGMYWnXhytuvj4zieLXUiaRIckwkBqUMflHjZ8qrWlDWttFt3o5AZkIeZMtKRc2GpR29jEK/R82rmgoTLjP6LtTv7gbRRT8sJanfKwwbvD6ru7RLpKq3hYAcNIsv/cefKwB2v1zQvxk6W2j8POO5srbBxrhThJ7K8hy1OxnMVuQ8qZQkhzm+cVrUE/JywidgemMJPSBp+6WhQgmbnJN+6KZRedbUmbx7qIhSiac7PLaiFA5Mf33n7WuZvXGWSc+gBAAA=)

```json
{
  "schema": "model FlightBooking {\n    passengers: Passenger[]\n}\nmodel Passenger {\n    name: PersonName inherits String\n}",
  "query": "given {\n    flight: FlightBooking = {\n        passengers: [\n            { name: \"Jim\" },\n            { name: \"Jill\" }\n        ]\n    }\n}\nfind {\n    passengerName: PersonName\n    allNames: PersonName[]\n}",
  "parameters": {},
  "stubs": [],
  "expectedJson": "{\"passengerName\": null, \"allNames\": [\"Jim\", \"Jill\"]}"
}
```

---

##### Cause 2: Type not reachable (missing service or stub)

TaxiQL automatically calls services to populate fields not present in the source data. If no service operation returns the needed type — or the service exists but no matching stub is provided — the field will be null.

**Example — missing service stub causes null:**

```taxi
model Order {
    customerId: CustomerId inherits String
    amount: OrderAmount inherits Decimal
}
model Customer {
    @Id id: CustomerId
    name: CustomerName inherits String
}
service CustomerService {
    operation getCustomer(CustomerId): Customer
}
service OrderService {
    operation getOrders(): Order[]
}
```

```taxi
find { Order[] } as {
    amount: OrderAmount
    customerName: CustomerName   // ❌ null — getCustomer has no stub
}[]
```

**Fix — provide the stub:**

```taxi
// With getCustomer stubbed, discovery works and customerName is populated
find { Order[] } as {
    amount: OrderAmount
    customerName: CustomerName   // ✅ "Alice"
}[]
```

**Diagnosis checklist:**
- Does a service operation exist that returns the needed type?
- Does the service operation accept a parameter that can be derived from the current data (e.g., `CustomerId` is available from `Order`)?
- Is the service properly annotated with `@HttpOperation` or equivalent so TaxiQL can discover it?
- In tests/validation, is a stub provided for the operation?

[Reproducer — null without stub](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/32RP0/DMBDFv4p1UytFSIzxBCpLGYpExziDca6tS2IH20EgK9-dc/OPqogsyZ2ff-_eJYJXJ2wkcGhshTV7cRU6FoVh9KjOB9ug21acbeZvps0JnQ6e7YPT5jhoZWM7E_gAeLwUi_AJlW5kLUwvzOAz4Sarh8S9shn6Rja4dHdU3doT1KP71Apn4X6sR7pt0cmgrWFHDJNmtXitF4sr3CXMf6yLwK_WY-6ipOuQwUeH7pt2etCmYnE6Yz2TfsL8sa_rpe9ukhO7KIneSkdVIGPgsc_Ah-6NPosI82xJTv7zhHTLoW8tJaN2EQUs_1YAZwI29wIyeg9zpV6e3-V5nwxRnezWtF0AfpC1xwyUNZVORrJ-HblpgrIvSf3VogpYPXtryCzeMJPN75TpwHR1nTbng1Tv2wp4alBUZ88EG8r-B-DaNQmuAgAA)

```json
{
  "schema": "model Order {\n    customerId: CustomerId inherits String\n    amount: OrderAmount inherits Decimal\n}\nmodel Customer {\n    @Id id: CustomerId\n    name: CustomerName inherits String\n}\nservice CustomerService {\n    operation getCustomer(CustomerId): Customer\n}\nservice OrderService {\n    operation getOrders(): Order[]\n}",
  "query": "find { Order[] } as {\n    amount: OrderAmount\n    customerName: CustomerName\n}[]",
  "parameters": {},
  "stubs": [
    {"operationName": "getOrders", "response": "[{\"customerId\": \"C1\", \"amount\": 99.99}]"}
  ],
  "expectedJson": "{\"amount\": 99.99, \"customerName\": null}"
}
```

[Reproducer — populated with stub](http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WRMW-DMBCF_wp2UyKhSh1hapQu6ZBKzYgZXHNJ3IJNbVO1Qvz3nuMAJYkqlQXf8fy-d0cHTh6xFpBBbUqs2LMt0bKOa0aPbJ03NdpNmbH1eGZKH9Eq79jOW6UPUStq02qfRYPVqZiEjyhVLSque64jZ7AbUA_Bd4aJfS1qnLpbqq7xZOrQfiqJo3B3rs_upkErvDKaHdAPmsXEWk6Imd1pmL-8TgK3WJ7nzgu6Dgl8tGi_aad7pUvWDd9Yz4QbbG7sa7707dXk5J0X5N4IS5UnMGRdn4Dz7Ssd8w7GbEFO_DEh3bLoGkOTUTvvOEz_lkPGOKzvOST0jrlCL03v0rQPQJRHs9FN6yHbi8phAtLoUgWQqF7OviFB0Sc3QwxTzGNQCnVBD787dlYV7ZxD_z98QeqvBqXH8skZHSEXIwXO7yVf8ZwX8n1TQqbbqqJ1W_NGjrHsfwCdJvU9MgMAAA==)

```json
{
  "schema": "model Order {\n    customerId: CustomerId inherits String\n    amount: OrderAmount inherits Decimal\n}\nmodel Customer {\n    @Id id: CustomerId\n    name: CustomerName inherits String\n}\nservice CustomerService {\n    operation getCustomer(CustomerId): Customer\n}\nservice OrderService {\n    operation getOrders(): Order[]\n}",
  "query": "find { Order[] } as {\n    amount: OrderAmount\n    customerName: CustomerName\n}[]",
  "parameters": {},
  "stubs": [
    {"operationName": "getOrders", "response": "[{\"customerId\": \"C1\", \"amount\": 99.99}]"},
    {"operationName": "getCustomer", "response": "{\"id\": \"C1\", \"name\": \"Alice\"}"}
  ],
  "expectedJson": "{\"amount\": 99.99, \"customerName\": \"Alice\"}"
}
```

---

##### Cause 3: Values excluded from scope

Projection scope controls which types are available during transformation. If you explicitly modify scope using `(Type) ->` syntax, types not included in the scope specification become unavailable.

```taxi
// Default: Film and all its fields are in scope
find { Film } as {
    title: Title         // ✅ From Film
    cast: Actor[]
}

// Scope modified: only Actor[] in scope — Film fields are now null
find { Film } as (Actor[]) -> {
    actorName: ActorName  // ✅ From Actor[]
    title: Title          // ❌ null — Film is not in scope
}[]

// Fix: include both Film and Actor[] in scope
find { Film } as (Film, Actor[]) -> {
    actorName: ActorName  // ✅ From Actor[]
    title: Title          // ✅ From Film
}[]
```

**Diagnosis:** If a field returns null after you added a scope modification (`(SomeType) ->`), check that all types you need are included in the scope declaration.

---

##### Quick diagnostic checklist

| Symptom | Check |
|---------|-------|
| Field is null but the data exists in the source | Is the type ambiguous? Try `TypeName[]` — if you get results, ambiguity was the cause |
| Field is null after calling a service | Is there a service operation that returns the needed type? Is `@Id` set correctly for the lookup key? |
| Field is null after modifying scope | Is the source type included in `(Type1, Type2) ->`? |
| All fields on an enrichment object are null | Check that the service stub response matches the schema (field names and types) |

---
