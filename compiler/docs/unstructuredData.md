 # Handling unstructured data
 
 Sometimes you have services that serve data which don't have a neat & tidy structure (such as legacy systems).
 
 It's still possible to define contracts for the messages produced by these systems.
 
 This example shows defining a contract for an XML message (assuming that no XSD exists, and that generating one
 is non-trivial.  If an XSD exists, it should be imported and extended as necessary using type extensions)
 
 ```taxi
type Money {
    amount : MoneyAmount as Decimal
    currency : Currency as String
} 

type Instrument as String
type NearLegNotional inherits Money
type FarLegNotional inherits Money

type LegacyTradeNotification {
    instrument : Instrument by xpath("/some/xpath")
    nearLegNotional : NearLegNotional {
        amount by xpath("/legs[0]/amount")
        currency by xpath("/legs[0]/currency")
    }
    farLegNotional : FarLegNotional {
        amount by xpath("/legs[1]/amount")
        currency by xpath("/legs[1]/currency")
    }
}

```

This shows the use of accessors.
We support two types of accessors:`xpath()` and `jsonPath()`

## Handling mixed message types

Sometimes you need to consume messages where you could receive messages of multiple different
types, with mixed contracts.  (eg., a queue of notifications with multiple different publishers).

For this, use Union types and discriminators:

```taxi
union type TradeNotification {
    LegacyTradeNotification by xpath("//something/")
    AnotherTypeTradeNotification by xpath("/somethingElse")
}
```


