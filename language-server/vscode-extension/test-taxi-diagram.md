# Taxi Diagram Test

This demonstrates the CodeLens-based diagram preview.

## Example Diagram

```taxi-diagram
Person
PersonService
films.Film
```

## Instructions

1. Open this file in VSCode
2. You should see a "🔀 Preview Diagram" CodeLens above the taxi-diagram block
3. Click it to open the diagram in a side panel
4. The diagram will be rendered using the same query plan visualization used in notebooks

## Notes

- The diagram panel opens in `ViewColumn.Beside` (side-by-side with the markdown)
- Uses the existing `taxiql/getDiagramData` LSP endpoint
- Reuses the notebook renderer bundle for consistent visualization
- Element names are extracted from the code block (one per line)
