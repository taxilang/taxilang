# TaxiQL Notebook Support

This extension now supports TaxiQL notebooks, providing an interactive environment for authoring and executing TaxiQL queries.

## Features

### Notebook Type
- **File Extension**: `.taxiqnb`
- **Notebook Type**: `taxiql-notebook`

### Supported Cell Languages
1. **TaxiQL** (`taxiql`) - Query cells for writing TaxiQL queries
2. **TaxiQL Stubs** (`taxiql-stubs`) - Stub definition cells (JSON format)
3. **Markdown** - Documentation and notes

### Cell Execution

#### Query Plan Visualization
Execute a TaxiQL cell without stubs to see the query execution plan:
- **Output MIME Type**: `application/vnd.taxi.plan+json`
- **Visualization**: Placeholder HTML (Rich React Flow visualization coming later)

#### Execute with Stubs
Attach stubs to a TaxiQL cell to execute with stubbed service responses:
- **Output MIME Type**: `application/vnd.taxi.results+json`
- **Visualization**: Placeholder HTML showing results (Rich table/JSON visualization coming later)

### Stub Management

Three commands are available for managing stubs in notebook cells:

1. **Attach Stubs...** - Attach stubs to a cell
2. **Change Stubs...** - Change the stubs attached to a cell
3. **Clear Stubs** - Remove stubs from a cell

Commands can be accessed via:
- Cell toolbar (inline buttons)
- Cell context menu
- Command palette (`Ctrl+Shift+P` / `Cmd+Shift+P`)

### Stub Metadata Format

Stubs are stored in cell metadata:
```json
{
  "taxi": {
    "stubsId": "stubs-123"
  }
}
```

## Testing the Notebook

### 1. Create a New Notebook

1. Open VSCode
2. Create a new file with the `.taxiqnb` extension
3. VSCode will recognize it as a TaxiQL Notebook

### 2. Add a Query Cell

1. Add a new code cell
2. Set the language to `taxiql` (or `TaxiQL`)
3. Write a sample TaxiQL query:
```taxiql
find { Customer }
```

### 3. Execute Without Stubs (Show Plan)

1. Click the "Run" button on the cell
2. You should see a placeholder visualization showing the query plan (stub data)
3. The output will be styled HTML with:
   - Blue header: "📊 Query Plan Visualization (Placeholder)"
   - JSON representation of the plan
   - Metadata section

### 4. Attach Stubs and Execute

1. Click "Attach Stubs..." button in the cell toolbar
2. Enter a stub ID (e.g., `stubs-123`)
3. Click "Run" again
4. You should see a placeholder visualization showing execution results
5. The output will be styled HTML with:
   - Green header: "✅ Execution Results (Placeholder)"
   - Execution summary (rows, execution time, status)
   - JSON representation of the results

### 5. Add a Stubs Cell (Optional)

1. Add a new code cell
2. Set the language to `taxiql-stubs`
3. Add stub JSON:
```json
{
  "id": "stubs-123",
  "responses": [
    {
      "service": "CustomerService",
      "operation": "getCustomers",
      "response": {
        "data": [
          { "id": 1, "name": "Alice" },
          { "id": 2, "name": "Bob" }
        ]
      }
    }
  ]
}
```
4. Execute the cell to validate the JSON

## Sample Notebook

Here's a complete example notebook structure in JSON format (`.taxiqnb` file):

```json
{
  "cells": [
    {
      "kind": "markdown",
      "language": "markdown",
      "value": "# TaxiQL Notebook Example\n\nThis notebook demonstrates TaxiQL query execution."
    },
    {
      "kind": "code",
      "language": "taxiql",
      "value": "find { Customer }",
      "metadata": {
        "taxi": {
          "stubsId": "stubs-123"
        }
      }
    },
    {
      "kind": "code",
      "language": "taxiql-stubs",
      "value": "{\n  \"id\": \"stubs-123\",\n  \"responses\": []\n}"
    }
  ]
}
```

## Architecture

### Extension Components

1. **NotebookSerializer** (`notebookSerializer.ts`)
   - Handles reading/writing `.taxiqnb` files
   - Persists cell metadata

2. **NotebookController** (`notebookController.ts`)
   - Executes notebook cells
   - Makes RPC calls to language server
   - Handles stub resolution

3. **StubManager** (`stubManager.ts`)
   - Manages stub attachments
   - Updates cell metadata
   - Provides commands for stub management

4. **Notebook Renderer** (`renderer/index.ts`)
   - Renders custom MIME types
   - Placeholder HTML for now
   - Will be replaced with rich UI later

### RPC Protocol

The notebook communicates with the TaxiQL language server via two RPC endpoints:

#### `taxiql/plan`
**Request:**
```typescript
{
  query: string,
  projectRoot: string,
  notebookPath: string
}
```

**Response:**
```typescript
{
  nodes: Array<{ id, label, type, metadata? }>,
  edges: Array<{ from, to, label? }>,
  metadata?: { query?, status?, warnings? }
}
```

#### `taxiql/executeWithStubs`
**Request:**
```typescript
{
  query: string,
  stubsId: string,
  projectRoot: string,
  notebookPath: string
}
```

**Response:**
```typescript
{
  data: any,
  metadata: {
    stubsId: string,
    rowCount?: number,
    executionTime?: string,
    status: string,
    warnings?: string[],
    trace?: string[]
  }
}
```

### Language Server Integration

The notebook controller currently uses stub responses when the language server endpoints are not implemented. To implement real functionality:

1. Add request handlers in `TaxiLanguageServer.kt` (or equivalent)
2. Implement the `taxiql/plan` endpoint to generate query plans
3. Implement the `taxiql/executeWithStubs` endpoint to execute queries with stubs
4. Return responses matching the protocol defined in `notebookProtocol.ts`

## Next Steps

### Immediate (Done)
- [x] Notebook serializer
- [x] Notebook controller
- [x] Basic renderer (placeholder HTML)
- [x] Stub management commands
- [x] Language server integration (stub responses)
- [x] Project context resolution

### Future Enhancements
- [ ] Rich React Flow visualization for query plans
- [ ] Rich table/JSON viewer for results
- [ ] Web-based stub editor UI
- [ ] Real service execution (not just stubs)
- [ ] Query plan node interaction (click to go to definition)
- [ ] Cell status bar items showing stub status
- [ ] Auto-complete for stub IDs
- [ ] Stub validation and preview

## Troubleshooting

### Notebook doesn't open
- Check that the file extension is `.taxiqnb`
- Verify the extension is activated (`onNotebook:taxiql-notebook`)

### Cells don't execute
- Check the language server is running
- Look for errors in the Output panel (View > Output > Taxi Language Server)
- Verify the notebook controller is registered (check console)

### Stubs not attaching
- Check cell metadata in the JSON view (right-click notebook > Open With > Text Editor)
- Verify the stub ID is stored in `metadata.taxi.stubsId`

### Renderer not working
- Check browser console in the webview (Help > Toggle Developer Tools)
- Verify the renderer output path in package.json matches the compiled location

## Development

### Building
```bash
cd language-server/vscode-extension
npm install --ignore-scripts
npm run compile
```

### Debugging
1. Open VSCode
2. Press F5 to launch Extension Development Host
3. Open a `.taxiqnb` file
4. Set breakpoints in TypeScript files
5. Use Debug Console for logging

### Testing
```bash
npm test
```

## File Structure

```
vscode-extension/
├── src/
│   ├── extension.ts              # Main extension entry point
│   ├── notebookSerializer.ts     # Notebook file I/O
│   ├── notebookController.ts     # Cell execution logic
│   ├── notebookProtocol.ts       # RPC protocol definitions
│   ├── stubManager.ts            # Stub management commands
│   └── renderer/
│       └── index.ts              # Notebook output renderer
├── out/                          # Compiled JavaScript
├── package.json                  # Extension manifest
└── tsconfig.json                 # TypeScript configuration
```
