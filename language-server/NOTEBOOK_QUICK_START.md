# TaxiQL Notebook - Quick Start Guide

## What Was Implemented

A complete end-to-end implementation for handling TaxiQL notebook cell execution in the JVM.

### Files Created

```
taxi-lang-service/src/main/java/lang/taxi/lsp/notebook/
└── NotebookService.kt                  ← Interface defining custom JSONRPC endpoints

taxi-lang-server-standalone/src/main/java/lang/taxi/lsp/
├── notebook/
│   └── TaxiNotebookService.kt          ← Implementation with placeholder logic
└── TaxiLanguageServerWithNotebooks.kt  ← Composite server wrapper
```

### Files Modified

```
taxi-lang-server-standalone/src/main/java/lang/taxi/lsp/
└── Launcher.kt                         ← Updated to register notebook service
```

## How to Test

### 1. Build the Language Server

```bash
mvnd clean compile -pl language-server/taxi-lang-service,language-server/taxi-lang-server-standalone -am
```

### 2. Build the VSCode Extension

```bash
cd language-server/vscode-extension
npm run compile
```

### 3. Launch Extension Development Host

- Open VSCode in the `language-server/vscode-extension` directory
- Press **F5** to launch the Extension Development Host
- This starts a new VSCode window with the extension loaded

### 4. Create a Test Notebook

In the Extension Development Host window:
1. **File > New File... > TaxiQL Notebook**
2. Add a TaxiQL cell with any query:
   ```taxiql
   find { Film }
   ```
3. Click the **▶ Run** button

### 5. Verify the Round-Trip

You should see output in the cell showing:
- A query plan with nodes and edges
- Metadata indicating "placeholder implementation"
- This proves the VSCode → JVM → VSCode round-trip is working!

Example output:
```json
{
  "nodes": [
    { "id": "1", "label": "Query Root", "type": "root" },
    { "id": "2", "label": "Service Call (Placeholder)", "type": "operation" }
  ],
  "edges": [
    { "from": "1", "to": "2", "label": "calls" }
  ],
  "metadata": {
    "status": "success",
    "message": "Query plan generated (placeholder implementation)"
  }
}
```

### 6. Check the Logs

**In the Extension Development Host:**
- Open **View > Output**
- Select **"Taxi Language Server"** from the dropdown
- Look for log entries like:
  ```
  [INFO] Received planQuery request for query: find { Film }...
  [INFO] Planning query from project root: /path/to/project
  ```

## What Each Endpoint Does

### 1. taxiql/plan (Query Planning)
**Triggered:** When you run a cell WITHOUT stubs attached
**Current behavior:** Returns placeholder plan with 2 nodes
**Next step:** Implement actual query planning logic

### 2. taxiql/executeWithStubs (Execution)
**Triggered:** When you run a cell WITH stubs attached
**Current behavior:** Returns test data rows
**Next step:** Implement actual query execution against stubs

### 3. taxiql/listOperations (Operation List)
**Triggered:** When you click "Attach Stubs..." in the cell toolbar
**Current behavior:** Returns 3 hardcoded operations
**Next step:** Load actual operations from the Taxi schema

## Testing with Stubs

To test the `executeWithStubs` endpoint:

1. Add a TaxiQL cell with a query
2. Click the **three dots (...)** in the cell toolbar
3. Select **"Attach Stubs..."**
4. In the stub editor, enter any stub ID (e.g., "test-stubs")
5. Click **"Use This Stub"**
6. Click **▶ Run** on the cell

You should see different output showing execution results:
```json
{
  "data": [
    { "id": 1, "name": "Test Row 1", "value": "JVM response working!" },
    { "id": 2, "name": "Test Row 2", "value": "Round-trip successful!" }
  ],
  "metadata": {
    "stubsId": "test-stubs",
    "rowCount": 2,
    "executionTime": "42ms",
    "status": "success"
  }
}
```

## Architecture at a Glance

```
┌──────────────────────────────────────────────────┐
│ VSCode Extension (TypeScript)                    │
│ notebookController.ts                            │
│   - User clicks "Run"                            │
│   - languageClient.sendRequest("taxiql/plan")    │
└───────────────────┬──────────────────────────────┘
                    │
                    │ JSONRPC over stdio
                    ↓
┌──────────────────────────────────────────────────┐
│ JVM Language Server                              │
│ TaxiLanguageServerWithNotebooks                  │
│   ├─ TaxiLanguageServer (standard LSP)           │
│   └─ TaxiNotebookService (notebook endpoints)    │
│       ├─ planQuery()                             │
│       ├─ executeWithStubs()                      │
│       └─ listOperations()                        │
└──────────────────────────────────────────────────┘
```

## Next Implementation Steps

Now that the plumbing is working, implement the actual logic:

### In `TaxiNotebookService.kt`:

1. **planQuery()**:
   - Use `compilerService` to parse the query
   - Generate a real query execution plan
   - Return meaningful nodes and edges

2. **executeWithStubs()**:
   - Parse the query
   - Load stub data (decide where stubs are stored)
   - Execute and return real results

3. **listOperations()**:
   - Load the Taxi schema from `projectRoot`
   - Extract services and operations
   - Return real operation metadata

## Troubleshooting

### "Language server not connected"
- Check the Output panel for errors
- Restart the Extension Development Host
- Rebuild the extension with `npm run compile`

### "Endpoint not available"
- Verify the language server compiled successfully
- Check that TaxiLanguageServerWithNotebooks is being created in Launcher.kt
- Look for exceptions in the Output panel

### No output appears
- Check if the cell is actually executing (execution order should increment)
- Look for errors in the Developer Tools console (Help > Toggle Developer Tools)
- Add more logging to TaxiNotebookService.kt

## Success Indicators

✅ **Round-trip working** when:
- You can run a TaxiQL cell and see output
- The output contains placeholder data from the JVM
- Logs show "Received planQuery request" or similar
- No errors in the Output panel

✅ **Ready for implementation** when:
- All three endpoints can be called from VSCode
- Each endpoint returns placeholder data
- Logs confirm requests are reaching the JVM
- The build completes successfully

## Questions?

See [NOTEBOOK_JVM_IMPLEMENTATION.md](./NOTEBOOK_JVM_IMPLEMENTATION.md) for detailed architecture documentation.
