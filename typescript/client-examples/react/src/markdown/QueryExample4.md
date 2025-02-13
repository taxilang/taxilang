### What This Query Does

1. **Purpose**:  
   The query subscribes to a Kafka topic created and managed by **Nebula**, which continuously broadcasts announcements about new film releases. These announcements are represented by the `demo.netflix.NewFilmReleaseAnnouncement` data structure.

2. **Data Source**:
    - The `NewFilmReleaseAnnouncement` events are published on a Kafka topic as part of the test ecosystem provided by **Nebula**.
    - Nebula ensures the Kafka topic is initialized and simulates broadcasting events on the `release` topic every 5 seconds, as specified in the configuration.

3. **TaxiQL Integration**:
    - The `.taxi` files in the workspace define the services and data contracts for the Kafka operations.
    - These `.taxi` definitions serve as the schema for the `NewFilmReleaseAnnouncement`, detailing its fields, their types, and how the data can be consumed.

### How TaxiQL Leverages Kafka

- **Streaming Queries**:  
  The `stream` keyword in TaxiQL allows real-time data to be consumed as events are broadcast on the Kafka topic. This makes it suitable for scenarios requiring live updates, such as displaying newly released films in a UI.

- **Schema-Driven Development**:  
  The `.taxi` files provide a declarative contract for interacting with the Kafka topic. This ensures type safety and compatibility between producers (Kafka publishers) and consumers (TaxiQL queries).

- **Real-Time Event Handling**:  
  The system processes each `NewFilmReleaseAnnouncement` event as soon as it is published, enabling real-time workflows, such as notifying users or updating a dashboard.
