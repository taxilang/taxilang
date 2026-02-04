# Taxi Diagram Cells in Notebooks

## Overview

You can now create diagram cells in TaxiQL notebooks to visualize types and services. These cells work just like the markdown taxi-diagram blocks but are integrated into the notebook workflow.

## How to Use

### 1. Create a New Diagram Cell

In your TaxiQL notebook:
1. Add a new code cell
2. Change the cell language to **"Taxi Diagram"** (use the language selector in the cell)
3. Enter type and service names, one per line

### 2. Cell Content Format

```
Person
PersonService
films.Film
orders.Order
```

**Features:**
- One type or service name per line
- Lines starting with `#` are treated as comments
- Empty lines are ignored
- No special syntax required

### 3. Execute the Cell

- Click the ▶ Run button or use the execute keyboard shortcut
- The cell will call the language server's `taxiql/getDiagramData` endpoint
- The diagram is rendered using the same query plan visualization as TaxiQL queries

## Example Notebook Structure

```
┌─────────────────────────────────────┐
│ Markdown Cell                       │
│ # My Service Architecture           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Taxi Diagram Cell                   │
│ Person                              │
│ PersonService                       │
│ films.Film                          │
│ films.FilmService                   │
└─────────────────────────────────────┘
  ↓ (Execute)
┌─────────────────────────────────────┐
│ [Diagram Output]                    │
│ [Interactive graph visualization]   │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ TaxiQL Query Cell                   │
│ find { Person[] }                   │
└─────────────────────────────────────┘
```

## Implementation Details

### What Happens When You Execute:

1. **Content Parsing**: Cell content is split by lines, trimmed, and filtered
2. **LSP Call**: Names are sent to `taxiql/getDiagramData` endpoint
3. **Diagram Generation**: Server generates diagram using `QueryPlanDiagramBuilder`
4. **Rendering**: Output uses `application/vnd.taxi.queryplan+json` mime type
5. **Visualization**: Notebook renderer displays the interactive diagram

### Cell Output

The diagram cell outputs are rendered using the same visualization component as:
- Query plan diagrams in TaxiQL cells
- Markdown taxi-diagram previews (CodeLens)

This ensures consistent visualization across all diagram features.

## Advantages Over Markdown Diagrams

✅ **Integrated Workflow**: Part of the notebook execution flow
✅ **Live Results**: Execute and see diagram inline
✅ **Cell Metadata**: Can attach to cells, save with notebook
✅ **No CodeLens Required**: Direct execution, no extra clicks
✅ **Output History**: Diagram output is saved with notebook

## Supported Languages in Notebooks

- **taxi**: TaxiQL queries
- **taxi-diagram**: Diagram cells (NEW)
- **taxiql-stubs**: Stub configuration
- **markdown**: Documentation cells

## Error Handling

If execution fails:
- Invalid type/service names: Error shows which names couldn't be found
- No language client: Error indicates LSP not initialized
- Empty cell: Shows helpful message asking for content

## Tips

- Use comments (`#`) to document what types you're including
- Group related types/services in the same diagram
- Execute after schema changes to update the visualization
- Combine with TaxiQL queries to show both architecture and data flow
