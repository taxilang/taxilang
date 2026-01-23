# TaxiQL Notebook JVM Implementation

This document describes the JVM implementation for handling TaxiQL notebook operations.

## Overview

The implementation allows VSCode notebook cells to invoke methods in the JVM language server via custom JSONRPC endpoints. When a user clicks "run" on a TaxiQL cell, the request flows from the VSCode extension through the language server to the JVM handlers.

## Architecture

### Component Structure

```
┌─────────────────────────────────────────────────────────────────┐
│ VSCode Extension (TypeScript)                                   │
│ - notebookController.ts: Handles cell execution                 │
│ - Sends JSONRPC requests via LanguageClient                     │
└────────────────┬────────────────────────────────────────────────┘
                 │ JSONRPC over stdio
                 ↓
┌─────────────────────────────────────────────────────────────────┐
│ Language Server Launcher (taxi-lang-server-standalone)          │
│ - Launcher.kt: Creates composite server                         │
│ - TaxiLanguageServerWithNotebooks: Routes requests              │
└────────────────┬────────────────────────────────────────────────┘
                 │
         ┌───────┴────────┐
         ↓                ↓
┌────────────────┐  ┌──────────────────────────────────────┐
│ TaxiLanguage   │  │ TaxiNotebookService                  │
│ Server         │  │ (taxi-lang-server-standalone)        │
│ (Standard LSP) │  │ - planQuery()                        │
│                │  │ - executeWithStubs()                 │
│                │  │ - listOperations()                   │
└────────────────┘  └──────────────────────────────────────┘
                              ↑
                              │ Uses
                    ┌─────────┴──────────┐
                    │ NotebookService    │
                    │ (taxi-lang-service)│
                    │ - Interface only   │
                    └────────────────────┘
```

### Key Files

#### taxi-lang-service (Core Service Interface)
- **`NotebookService.kt`**: Interface defining the custom JSONRPC endpoints
  - `@JsonRequest("taxiql/plan")`: Generate query execution plan
  - `@JsonRequest("taxiql/executeWithStubs")`: Execute query with stubs
  - `@JsonRequest("taxiql/listOperations")`: List operations from schema
  - Data classes for requests/responses matching TypeScript protocol

#### taxi-lang-server-standalone (Implementation)
- **`TaxiNotebookService.kt`**: Concrete implementation of NotebookService
  - Currently returns placeholder data to validate plumbing
  - Ready for actual query planning/execution logic
  - Logs all requests for debugging

- **`TaxiLanguageServerWithNotebooks.kt`**: Composite server wrapper
  - Implements both LanguageServer and NotebookService interfaces
  - Delegates standard LSP requests to TaxiLanguageServer
  - Delegates notebook requests to TaxiNotebookService
  - Allows single JSONRPC launcher to handle all requests

- **`Launcher.kt`**: Updated to create composite server
  - Creates both language server and notebook service
  - Wraps them in TaxiLanguageServerWithNotebooks
  - Registers with LSPLauncher

## JSONRPC Protocol

### Endpoints

#### 1. taxiql/plan
Generate a query execution plan.

**Request:**
```json
{
  "query": "find { Film }",
  "projectRoot": "/path/to/project",
  "notebookPath": "/path/to/notebook.taxiqnb"
}
```

**Response:**
```json
{
  "nodes": [
    {
      "id": "1",
      "label": "Query Root",
      "type": "root",
      "metadata": { "query": "find { Film }" }
    }
  ],
  "edges": [
    { "from": "1", "to": "2", "label": "calls" }
  ],
  "metadata": {
    "status": "success",
    "projectRoot": "/path/to/project"
  }
}
```

#### 2. taxiql/executeWithStubs
Execute a query using stubbed responses.

**Request:**
```json
{
  "query": "find { Film }",
  "stubsId": "film-stubs-123",
  "projectRoot": "/path/to/project",
  "notebookPath": "/path/to/notebook.taxiqnb"
}
```

**Response:**
```json
{
  "data": [
    { "id": 1, "name": "Test Row 1", "value": "JVM response working!" }
  ],
  "metadata": {
    "stubsId": "film-stubs-123",
    "rowCount": 1,
    "executionTime": "42ms",
    "status": "success",
    "warnings": ["This is a placeholder implementation"],
    "trace": ["Received request at TaxiNotebookService"]
  }
}
```

#### 3. taxiql/listOperations
List available operations from the Taxi schema.

**Request:**
```json
{
  "projectRoot": "/path/to/project"
}
```

**Response:**
```json
{
  "operations": [
    {
      "service": "CustomerService",
      "operation": "getCustomer",
      "returnType": "Customer",
      "metadata": {
        "httpMethod": "GET",
        "path": "/customers/{id}"
      }
    }
  ]
}
```

## How It Works

### Request Flow

1. **User clicks "Run"** on a TaxiQL cell in VSCode
2. **NotebookController** (TypeScript) calls `languageClient.sendRequest("taxiql/plan", request)`
3. **JSONRPC message** is sent over stdio to the language server process
4. **LSPLauncher** receives the message and routes to TaxiLanguageServerWithNotebooks
5. **TaxiLanguageServerWithNotebooks** delegates to TaxiNotebookService
6. **TaxiNotebookService** processes the request and returns a response
7. **Response** flows back through JSONRPC to the VSCode extension
8. **NotebookController** renders the result in the notebook cell

### Extension Points

The current implementation is a **strawman** with placeholder logic. To implement the actual functionality:

1. **In TaxiNotebookService.planQuery()**:
   - Parse the TaxiQL query using the compiler
   - Generate a query execution plan
   - Convert to graph structure (nodes + edges)

2. **In TaxiNotebookService.executeWithStubs()**:
   - Parse the TaxiQL query
   - Load stub data by stubsId (TBD: where stubs are stored)
   - Execute the query against stubbed responses
   - Return the results

3. **In TaxiNotebookService.listOperations()**:
   - Use TaxiCompilerService to load the schema from projectRoot
   - Iterate through services and extract operations
   - Return operation metadata

## Testing

### Manual Test

1. Build the language server:
   ```bash
   mvnd clean compile -pl language-server/taxi-lang-service,language-server/taxi-lang-server-standalone -am
   ```

2. Build the VSCode extension:
   ```bash
   cd language-server/vscode-extension
   npm run compile
   ```

3. Launch the Extension Development Host (F5 in VSCode)

4. Create a new TaxiQL notebook (File > New File... > TaxiQL Notebook)

5. Add a TaxiQL cell with a query:
   ```taxiql
   find { Film }
   ```

6. Click the "Run" button

7. Verify the output shows:
   - A query plan with placeholder nodes
   - Metadata indicating the JVM was reached

### Expected Output

When you run a cell, you should see output like:

```json
{
  "nodes": [
    {
      "id": "1",
      "label": "Query Root",
      "type": "root"
    },
    {
      "id": "2",
      "label": "Service Call (Placeholder)",
      "type": "operation"
    }
  ],
  "edges": [
    {
      "from": "1",
      "to": "2",
      "label": "calls"
    }
  ],
  "metadata": {
    "status": "success",
    "message": "Query plan generated (placeholder implementation)"
  }
}
```

### Debugging

#### VSCode Extension Logs
- Open Output panel: View > Output
- Select "Taxi Language Server" from dropdown
- Look for log messages from the JVM

#### Language Server Logs
The TaxiNotebookService logs all requests:
```
[INFO] Received planQuery request for query: find { Film }...
[INFO] Planning query from project root: /path/to/project
```

## Next Steps

To complete the implementation:

1. **Implement Query Planning**
   - Parse TaxiQL queries
   - Generate execution plans
   - Convert to node/edge graph structure

2. **Implement Query Execution**
   - Define stub storage mechanism
   - Load stubs by ID
   - Execute queries against stubbed data

3. **Implement Operation Listing**
   - Load Taxi schema from project
   - Extract service operations
   - Include metadata (HTTP methods, parameters, etc.)

4. **Add Error Handling**
   - Handle invalid queries
   - Handle missing projects
   - Return meaningful error messages

5. **Add Tests**
   - Unit tests for each endpoint
   - Integration tests for end-to-end flow
   - Test error scenarios

## Benefits of This Approach

1. **Clean Separation**: Core service interface in taxi-lang-service, implementation in standalone
2. **Reusable**: NotebookService interface could be implemented differently for other contexts
3. **Standard JSONRPC**: Uses LSP4J's built-in mechanisms, no custom protocol parsing
4. **Type-Safe**: Kotlin data classes ensure type safety on the JVM side
5. **Testable**: Services can be tested independently of the LSP infrastructure

## Troubleshooting

### "Endpoint not available" errors
- Verify the language server is running (check Output panel)
- Ensure the composite server is being created (check Launcher.kt)
- Look for exceptions in the language server logs

### Requests timeout
- Check if the JVM is processing requests (add log statements)
- Verify the CompletableFuture is being returned correctly
- Ensure no blocking operations on the JSONRPC thread

### Type mismatches
- Verify TypeScript interfaces match Kotlin data classes
- Check JSON serialization of complex types
- Review the @JsonRequest method signatures
