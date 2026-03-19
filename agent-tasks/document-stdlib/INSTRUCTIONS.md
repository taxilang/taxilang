# Stdlib Documentation Agent — Instructions

## Your Goal

Work through `stdlib-doc-status.md` and document every function marked `Not Started`. For each function you will:
1. Write or improve the in-source documentation
2. Author 1–3 runnable examples
3. **Validate every example** against the query test service before committing anything
4. Update the status tracker

---

## Before You Start

1. Read `TAXIQL.md` — you need to understand the query language to write correct examples.
2. Locate the stdlib source files. They live at `core-types/src/main/java/lang/taxi/functions/stdlib`. Every function is a Kotlin `object` implementing `FunctionApi`. You will be editing these files directly.
3. Confirm the validation service is reachable: `GET http://localhost:9500/` should respond.

---

## Status Lifecycle

Update `stdlib-doc-status.md` at each transition. Valid statuses are:

| Status            | Meaning                                                         |
|-------------------|-----------------------------------------------------------------|
| `Not Started`     | Not yet touched                                                 |
| `In Progress`     | You are actively working on it right now                        |
| `Awaiting Review` | Docs written, all examples validated as `isValid: true`         |
| `Has Problems`    | One or more examples exposed a platform bug (see Bug Reporting) |

**Only one function should be `In Progress` at a time.** Complete a function fully before moving to the next.

---

## Per-Function Workflow

Work through functions in the order they appear in the status file. For each:

### Step 1 — Mark In Progress
Update the status file: `Not Started` → `In Progress`

### Step 2 — Read the existing source and implementation
Find the function's Kotlin object in the stdlib source at `core-types/src/main/java/lang/taxi/functions/stdlib`. Also find the function's implementation at `/home/martypitt/dev/vyne/vyne-core-types/src/main/java/com/orbitalhq/models/functions/stdlib` and read it.

**Reading the implementation is not optional.** Documentation written purely from a function's name or signature is often subtly wrong — edge cases, error behaviour, and type handling quirks only become visible in the code. Common things to look for:
- What happens on empty collections or null inputs?
- Are there any type coercions happening?
- Does the function throw, or return a sentinel value on bad input?
- Are there overloads or generic constraints worth documenting?

From both files, note:
- Is there existing documentation? Does it need improving or correcting?
- Does the implementation reveal any behaviour that isn't obvious from the signature?

### Step 3 — Write the documentation string
Edit the `taxi` field. The documentation block goes **before** the function signature, wrapped in `[[` and `]]`. Format:

```
[[
Short 1–2 sentence description of what the function does.

(Optional) A longer paragraph for any important nuance, edge cases, or usage context that isn't obvious from the signature.

```taxi
// A brief illustrative code comment showing typical use
```
]]
declare extension function ...
```

Requirements:
- The short description must make sense without reading any examples
- Do not pad with filler. If a longer paragraph isn't needed, omit it.
- The inline code block in the docstring is illustrative only — it does not need to be validated

### Step 4 — Write examples
Add or update the `examples` field. The object must implement `HasRunnableExamples`:

```kotlin
object MyFunction : FunctionApi, HasRunnableExamples {
    override val examples: List<DocsSnippet> = listOf(
        DocsSnippet(
            markdown = "One sentence describing what this example demonstrates.",
            query = StubQueryMessage(
                schema = "...",   // Can be empty string "" if no schema needed
                query = "...",
                expectedJson = "..."
            )
        )
    )
}
```

**How many examples:**
- **1 example** — sufficient for simple functions where one call covers all behaviour
- **2–3 examples** — only if they demonstrate meaningfully different behaviour (e.g. different input types, edge cases like empty collections, different argument combinations). Three examples showing the same thing with different data is not useful.

**Writing good examples:**
- Keep schemas minimal — only define what the example actually needs
- Keep queries short and focused
- `expectedJson` must be exactly what the query will return — no extra fields, no missing fields
- If the function operates on literals (e.g. `upperCase("hello")`), you often don't need a schema at all

### Step 5 — Validate every example

> ⚠️ **Validation is mandatory. No example may appear in source unless it has passed validation.** Unvalidated examples are worse than no examples — they mislead users and erode trust in the documentation. If you cannot get an example to validate, either fix it or file a bug report. Do not write it to source and hope.

Before writing anything to the source file, validate each example by POSTing to `http://localhost:9500/api/validate`.
You MUST POST using a simple curl request. Do not try to prettify the request or response, as you do not have permission to use those tools.

**Request body:**
```json
{
  "schema": "...",
  "query": "...",
  "parameters": {},
  "stubs": [],
  "expectedJson": "..."
}
```

**Interpreting the response:**
```json
{
  "isValid": true | false,
  "errors": [...],
  "expected": ...,
  "actual": ...,
  "playgroundUrl": "..."
}
```

- If `isValid: true` — the example is correct. Record the `playgroundUrl`.
- If `isValid: false` — **do not assume it's a platform bug first.** Read `errors` carefully.
   - Most failures at this stage are authoring errors: wrong field name, incorrect `expectedJson`, type mismatch, bad TaxiQL syntax. Fix your example and retry.
   - Only escalate to a bug report after you have confirmed the query is correctly authored and the failure is not your mistake.

**Keep iterating until every example passes `isValid: true`. This is a hard requirement — do not proceed to Step 6 until it is met.**

### Step 6 — Write the validated examples into source
Only after all examples pass validation, update the Kotlin source file with:
- The updated `taxi` documentation string
- The `examples` list with all validated `DocsSnippet` entries

### Step 7 — Mark complete
- If all examples validated: update status to `Awaiting Review`
- If a confirmed platform bug was found: update status to `Has Problems` and write a bug report (see below)

---

## Bug Reporting

A bug is confirmed when **both** of these are true:
1. Your TaxiQL is correctly authored (you've double-checked against `TAXIQL.md` and fixed all your own mistakes)
2. The platform still returns incorrect results, crashes, or `isValid: false` for a query that should be valid

**Do not file a bug for your own authoring errors.**

When you have confirmed a bug:

1. Write a bug report as a markdown file at `problems/<function-name>.md` using this structure:

```markdown
# Bug Report: <FunctionName>

## Function
`taxi.stdlib.<functionName>`

## Summary
One sentence describing the bug.

## Expected Behaviour
What should happen.

## Actual Behaviour
What actually happens. Include the exact error message or actual output.

## Reproducer

POST to `/api/validate`:
\```json
{
  "schema": "...",
  "query": "...",
  "expectedJson": "..."
}
\```

Playground URL: <playgroundUrl from response>

## Notes
Any additional context. Is this a regression? Does it affect only certain input shapes?
```

2. Still write the documentation for the function. For any example that hit the bug, include it in the source with a comment noting it is broken, and reference the bug report.

3. Set status to `Has Problems`.

---

## Responding to Review Feedback

Review comments are left directly in `stdlib-doc-status.md`, inline with the function's status entry. **Before picking up any `Not Started` work, always scan the full status file for functions with unresolved review comments** — those take priority.

A function has unresolved review comments if its status is `Awaiting Review` or `Has Problems` and there are comments beneath it that haven't been marked as addressed.

For each function with unresolved comments:

1. Read every comment carefully before touching any source
2. Make the requested changes to the Kotlin source
3. Re-validate any examples that were affected — even if the example looked correct before, changes to schema or query may have broken it
4. Once all comments are addressed and all affected examples re-validated, update `stdlib-doc-status.md`: add a brief reply beneath each comment explaining what you changed, then set the status back to `Awaiting Review`

Do not mark a comment as resolved unless you have actually addressed it. "Acknowledged" is not "fixed".

---

## Quality Bar

Before marking any function `Awaiting Review`, confirm:
- [ ] The short description is accurate and self-contained
- [ ] Every example has a single-sentence `markdown` field explaining what it shows
- [ ] Every example has been validated with `isValid: true`
- [ ] Every example's `playgroundUrl` is recorded in the source as a comment
- [ ] No two examples demonstrate identical behaviour
- [ ] The Kotlin source compiles (check imports — `HasRunnableExamples`, `DocsSnippet`, `StubQueryMessage` must be imported)

## Best practices to follow in examples
TAXIQL.md contains a large amount of best practices to follow. Be sure to read that document carefully.

Below are a small collection of other practices to follow


### Use semantic types on models, rather than raw scalars.

```taxi
// x Wrong. Don't use raw scalar types
model Product {
   name: String
   tags: String[]
}


// ✅ Declare semantic types instead
type ProductTag inherits String
model Product {
  name :  ProductName inherits String // Using an inline declaration is fine
  tags : ProductTag[] // Can't use inline declarations when declaring an array 
}
```

### Using raw scalars as the result of an expression in a query is acceptable
Unlike when declaring fields on a model (which should always use semantic types), a raw scalar is acceptable as the
field type the result of a query.

```taxi
// ✅ Using raw scalars here is permitted
  given {                                                                                                                                                                                                      
     product: Product = { name: 'Laptop', tags: ['electronics', 'portable', 'work'] }                                                                                                                          
  }                                                                                                                                                                                                            
  find {                                                                                                                                                                                                       
     hasAnyTag: Boolean = ProductTag[].containsAny(['gaming', 'portable']) // <--- OK to declare as Boolean                                                                                                                                     
     hasNone: Boolean = ProductTag[].containsAny(['gaming', 'sports'])                                                                                                                                         
  }         
```

### Prefer type based access over property based access in expressions
Taxi is designed to be type-centric, rather than property-centric, as it keeps systems loosely coupled.

```taxi
// x Uses property based access
 given {                                                                                                                                                                                                      
     product: Product = { name: 'Laptop', tags: ['electronics', 'portable', 'work'] }                                                                                                                          
  }                                                                                                                                                                                                            
  find {                                                                   
     // avoid using product.tags.containsAny if possible, as it tightly couples the query to product.tags being a valid path
     hasAnyTag: Boolean = product.tags.containsAny(['gaming', 'portable'])                                                                                                                                       
     hasNone: Boolean = product.tags.containsAny(['gaming', 'sports'])                                                                                                                                         
  }                          
```

```taxi
// ✅ Using raw scalars here is permitted
  given {                                                                                                                                                                                                      
     product: Product = { name: 'Laptop', tags: ['electronics', 'portable', 'work'] }                                                                                                                          
  }                                                                                                                                                                                                            
  find {
     // uses the semantic type ProductTag[] - which keeps the code decoupled from the acutal source of ProductTag[]                                                                                                                                                                                                       
     hasAnyTag: Boolean = ProductTag[].containsAny(['gaming', 'portable']) // <--- OK to declare as Boolean                                                                                                                                     
     hasNone: Boolean = ProductTag[].containsAny(['gaming', 'sports'])                                                                                                                                         
  }         
```

