# Taxi Diagram Test

This file demonstrates the custom markdown renderer for taxi-diagram code blocks.

## Simple Diagram

```taxi-diagram
Person
PersonService
films.Film
```

## Another Example

Here's a more complex example:

```taxi-diagram
com.example.Customer
com.example.CustomerService
orders.Order
orders.OrderService
```

## Regular Code Block

This is a regular taxi code block (not rendered as diagram):

```taxi
type Person {
   name: String
   age: Int
}
```

## Notes

- The taxi-diagram blocks show a placeholder for now
- Actual diagram rendering will be implemented later
- This works in both regular markdown files and notebook markdown cells
