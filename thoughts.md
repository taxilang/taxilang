# Taxi

Taxi is a language for defining resources and contracts between systems.
Similar to Swagger / RAML, and protobuf specs.  (Note - not a serialization format.)

## Design goals:

### Language agnostic
Taxi will (initially) compile to RAML, which can then be output
to many different languages.

Eventually, the RAML step should be removed.

### Concise & expressive
Steal ideas from Kotlin & GraphQL for expressiveness

### Extensible
API's are composable -- publishers define the contract, but consumers
inject metadata around how they are used.

Eg: Annotations for defining persistence, mappings, etc.
These are consumer extensions to the publishers contract.

## Non goals
 - Serialization, and therefore backwards compatibility.

# Ideas:

## Method contracts

In order to be able to programatically discover what purpose a 
function provides, we need to be able to describe it's output programatically.

I'm thinking something like:

```
// Given a Money type...
type Money {
    amount : Decimal
    currency : Currency
}

// .. convert it to a different currency 
fun convertCurrency(source : Money, target : Currency) : Money( currency = target )
```

Considerations:

 * In this scenario, the contract returns `source`, not `Money`.  This
  is to indicate that the other attributes of `source` are unchanged, and that
  the currency has changed.  But, this doesn't do a good job of explaining that the
  `amount` field has also changed.
  
  An alternative is to return `source( currency = target )`.  This is more descriptive
  wrt/ type, but less meaningful 
  
=== Displaying Mutations
Another consideration is if the contract should show the mutations.
eg:

```
// .. convert it to a different currency 
fun convertCurrency(source : Money, target : Currency) : Money( currency = target, amount! )
```

Here, we indicate that the amount has changed, by using the `!` operator: `amount!`
The contact doesn't specify how the mutation might be calculated.
