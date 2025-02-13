This stream query builds off of Example 4, with the following additions:
- **Projected Data**:
    - Maps fields: `name`, `id`, `description`, `platformName`, `price`, `rating`, `review`.
    - `platformName`, `price`, `rating` and `review` are fetched via separate REST API calls as defined in the `.taxi` services and hosted by Nebula.
- **Output**: An array of enriched film release data, including platform name, pricing, rating, and review text.
