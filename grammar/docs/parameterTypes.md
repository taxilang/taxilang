# Parameter Types

```
parameter type ClientRiskRequest {
   amount : Money(currency = 'GBP')
   clientId : ClientId
}
```

Signal that an object isn't part of the domain, and is constructed for sending data to / from operations.

eg: Where a function may be defined as:

```
operation calculateRisk(ClientId, Money)
```

However, some transports / protocols don't work well with collections of arguments.  
eg: RESTful services, where generally arguments are wrapped in a request object sent
in a POST.

For these situations, use `parameter type`s.

## Impact
There's no direct impact of declaring a `parameter type` within Taxi.
However, it indicates to tooling leveraging the schema that an object can be constructed based on what is currently known.

The inverse is also true:  tooling shouldn't't attempt to construct non-parameter types. 

