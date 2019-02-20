# Closed Types

> Note: This is a working name, and not a very good one.  Will be renamed to something more self-descriptive when I can come up with something.

```taxi
closed type Money {
    amount : MoneyAmount as Decimal
    currency : Currency as String
}
```

Closed types are types whose individual parts cannot be further decomposed without losing context.

For example, in the `Money` type above, neither the `amount` nor the `currency` make sense individually, they need each other to describe their full meaning.

## Composition of closed types

 A type may close over another type:
 
 ```
 type OrderItem { ... }
 closed type Order {
    items : OrderItem[]
    orderValue : OrderValue as Money
 }
 ```

In this case, it has been denoted that OrderItem and OrderValue may not be evaluated outside of the broader Order type.  In this sense,
even though OrderItem and OrderValue aren't closed types as top-level types, they are considered closed when accessed via an Order.

##Closed properties
A type may declare certain properties as closed.  This indicates that the specific fields may not be considered outside
of the context of the encapsulating type. 

Note - often, it may be preferrable to use a type alias, defining a more contextually appropriate type, than to use a closed property.

```taxi
type Trade {
   trader : UserId
   closed settlementCurrency : Currency
}
```

But, the preferred approach:

```taxi
type Trade {
   trader : UserId
   settlementCurrency : SettlementCurrency as Currency
}
```

## Embedded Aliases (or type refinements)
```taxi
type Money {
    value : BigDecimal
    currency : Currency as String
}

type trade {
    quote : Money {
        // Inline type refinements
        currency -> TradeCurrency /* == currency : TradeCurrency as Currency */
    }
}
```