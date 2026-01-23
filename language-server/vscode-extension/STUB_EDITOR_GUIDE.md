# TaxiQL Stub Editor - Architecture & Plumbing

This document describes the stub editor implementation and demonstrates the full communication plumbing from UI → Extension → Language Server (JVM).

## Overview

The stub editor is a webview-based UI that allows users to:
1. Browse available operations from the Taxi schema
2. Create/select stub IDs for mocking service responses
3. Attach stubs to TaxiQL query cells

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         VSCode UI                                │
│                                                                  │
│  ┌──────────────────┐                  ┌───────────────────┐   │
│  │  Notebook Cell   │                  │  Stub Editor      │   │
│  │  (TaxiQL Query)  │                  │  Webview Panel    │   │
│  │                  │                  │                   │   │
│  │  [Attach Stubs]  │──────────────────▶│  HTML/JS UI      │   │
│  └──────────────────┘                  └───────────────────┘   │
│                                               │                 │
│                                               │ postMessage     │
│                                               ▼                 │
│                                         ┌───────────────────┐   │
│                                         │  Extension Host   │   │
│                                         │  (TypeScript)     │   │
│                                         └───────────────────┘   │
│                                               │                 │
└───────────────────────────────────────────────┼─────────────────┘
                                                │
                                                │ LSP RPC
                                                ▼
                                    ┌───────────────────────┐
                                    │  Language Server      │
                                    │  (JVM/Kotlin)         │
                                    │                       │
                                    │  taxiql/listOperations│
                                    └───────────────────────┘
```

## Communication Flow

### 1. User Opens Stub Editor

```typescript
// User clicks "Attach Stubs..." button on a cell
StubManager.attachStubs(cell)
  ↓
// Opens webview panel
StubEditorPanel.show(extensionUri, languageClient)
  ↓
// Returns Promise<string | undefined> with stub ID
```

### 2. Webview Loads and Requests Operations

**Webview (HTML/JS):**
```javascript
// On load, request operations from extension
vscode.postMessage({
   type: 'getOperations',
   projectRoot: '/workspace'
});
```

### 3. Extension Receives Message and Calls Language Server

**Extension Host (stubEditor.ts):**
```typescript
handleMessage(message) {
   case "getOperations":
      await this.fetchOperations(message.projectRoot);
      // Calls language server via RPC
}

fetchOperations(projectRoot) {
   const request: ListOperationsRequest = {
      projectRoot
   };

   const response = await this.languageClient.sendRequest<ListOperationsResponse>(
      "taxiql/listOperations",
      request
   );

   // Send response back to webview
   this.panel.webview.postMessage({
      type: 'operationsResponse',
      operations: response.operations
   });
}
```

### 4. Language Server Processes Request (JVM)

**Language Server (Kotlin) - To Be Implemented:**
```kotlin
// In TaxiLanguageServer.kt or similar

@JsonRequest("taxiql/listOperations")
fun listOperations(params: ListOperationsRequest): ListOperationsResponse {
    val projectRoot = params.projectRoot

    // Parse Taxi schema from projectRoot
    // Extract service operations
    // Return list of operations

    return ListOperationsResponse(
        operations = listOf(
            Operation(
                service = "CustomerService",
                operation = "findCustomers",
                returnType = "Customer[]"
            ),
            // ... more operations
        )
    )
}
```

**For now (stubbed in extension):**
```typescript
// If endpoint not available, return stub data
catch (error) {
   this.panel.webview.postMessage({
      type: 'operationsResponse',
      operations: [
         { service: "CustomerService", operation: "findCustomers", ... },
         { service: "OrderService", operation: "findOrders", ... },
         { service: "ProductService", operation: "getProduct", ... }
      ]
   });
}
```

### 5. Webview Displays Operations

**Webview (HTML/JS):**
```javascript
// Handle response from extension
window.addEventListener('message', event => {
   if (event.data.type === 'operationsResponse') {
      const operations = event.data.operations;
      // Render operations in UI
      renderOperations(operations);
   }
});
```

### 6. User Selects Stub ID

**Webview (HTML/JS):**
```javascript
function selectStub() {
   const stubId = document.getElementById('stubId').value;
   vscode.postMessage({
      type: 'selectStub',
      stubsId: stubId
   });
}
```

### 7. Extension Updates Cell Metadata

**Extension Host (stubEditor.ts → stubManager.ts):**
```typescript
// stubEditor.ts resolves promise with stubId
if (this.resolveCallback) {
   this.resolveCallback(message.stubsId);
}

// stubManager.ts receives stubId and updates cell
const stubsId = await StubEditorPanel.show(...);
await this.updateCellMetadata(cell, stubsId);
```

## File Structure

```
src/
├── stubEditor.ts           # Webview panel management
│   ├── StubEditorPanel class
│   ├── Message handlers
│   ├── RPC calls to language server
│   └── HTML content generation
│
├── stubManager.ts          # Stub attachment logic
│   ├── Opens stub editor
│   ├── Updates cell metadata
│   └── Command handlers
│
├── notebookProtocol.ts     # RPC protocol definitions
│   ├── ListOperationsRequest
│   ├── ListOperationsResponse
│   └── Operation interface
│
└── extension.ts            # Wiring
    └── Connects stubManager to language client
```

## RPC Protocol

### Request: `taxiql/listOperations`

**TypeScript Interface:**
```typescript
interface ListOperationsRequest {
   projectRoot: string;
}
```

**Example Request:**
```json
{
   "projectRoot": "/home/user/my-taxi-project"
}
```

### Response: `taxiql/listOperations`

**TypeScript Interface:**
```typescript
interface ListOperationsResponse {
   operations: Operation[];
}

interface Operation {
   service: string;
   operation: string;
   returnType: string;
   metadata?: {
      httpMethod?: string;
      path?: string;
      parameters?: Array<{ name: string; type: string }>;
   };
}
```

**Example Response:**
```json
{
   "operations": [
      {
         "service": "CustomerService",
         "operation": "findCustomers",
         "returnType": "Customer[]",
         "metadata": {
            "httpMethod": "GET",
            "path": "/customers"
         }
      },
      {
         "service": "OrderService",
         "operation": "getOrder",
         "returnType": "Order",
         "metadata": {
            "httpMethod": "GET",
            "path": "/orders/{id}",
            "parameters": [
               { "name": "id", "type": "String" }
            ]
         }
      }
   ]
}
```

## Testing the Plumbing

### 1. Launch Extension Development Host

```bash
cd language-server/vscode-extension
npm run compile
# Press F5 in VSCode
```

### 2. Open a TaxiQL Notebook

- Create or open a `.taxiqnb` file
- Add a TaxiQL query cell

### 3. Click "Attach Stubs..."

- Button appears in cell toolbar
- Stub editor opens in new panel

### 4. Observe the Flow

**In Browser Console (Help > Toggle Developer Tools):**
```
[Stub Editor] Stub editor loaded, requesting operations...
[Stub Editor] Received 3 operations from language server
```

**In Extension Host Console (Debug Console):**
```
taxiql/listOperations endpoint not available, using stub
```

**In Webview UI:**
- Status shows: "Connected to JVM ✓"
- Lists 3 operations (stub data)
- Each operation is clickable
- Stub ID field auto-fills when clicking operation

### 5. Select a Stub

- Enter or auto-fill stub ID
- Click "Use This Stub"
- Cell metadata updated
- Editor closes
- Notification: "Stubs 'xyz' attached to cell"

### 6. Verify Cell Metadata

Right-click notebook → Open With → Text Editor:
```json
{
  "cells": [
    {
      "kind": "code",
      "language": "taxiql",
      "value": "find { Customer }",
      "metadata": {
        "taxi": {
          "stubsId": "customerservice-findcustomers"
        }
      }
    }
  ]
}
```

## Implementing the Language Server Endpoint

To replace the stub data with real operations from your Taxi schema:

### 1. Add Request Handler (Kotlin)

**File:** `taxi-lang-service/src/main/java/lang/taxi/lsp/TaxiLanguageServer.kt`

```kotlin
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

data class ListOperationsRequest(
    val projectRoot: String
)

data class ListOperationsResponse(
    val operations: List<Operation>
)

data class Operation(
    val service: String,
    val operation: String,
    val returnType: String,
    val metadata: Map<String, Any>? = null
)

class TaxiLanguageServer : LanguageServer {
    // ... existing code ...

    @JsonRequest("taxiql/listOperations")
    fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse> {
        return CompletableFuture.supplyAsync {
            val projectRoot = params.projectRoot

            // 1. Load Taxi schema from projectRoot
            val schema = loadSchema(projectRoot)

            // 2. Extract operations from services
            val operations = schema.services.flatMap { service ->
                service.operations.map { operation ->
                    Operation(
                        service = service.name,
                        operation = operation.name,
                        returnType = operation.returnType.toString(),
                        metadata = mapOf(
                            "httpMethod" to operation.httpMethod,
                            "path" to operation.path
                        )
                    )
                }
            }

            ListOperationsResponse(operations)
        }
    }
}
```

### 2. Test with Real Data

Once implemented:
1. Open stub editor
2. Operations will load from your Taxi schema
3. Extension host console shows successful RPC call
4. No "endpoint not available" warning

## Current State

✅ **Implemented:**
- Webview UI with styled HTML
- Message passing (webview ↔ extension)
- RPC call to language server
- Graceful fallback with stub data
- Cell metadata updates
- User can select stub IDs

⏳ **Stubbed:**
- `taxiql/listOperations` endpoint (returns hardcoded data)

🔮 **Future Enhancements:**
- Rich stub response editor
- Validation and preview
- Stub library management
- Auto-complete for stub IDs
- Inline stub creation

## Debugging

### Enable Extension Logging

**Output Panel:**
- View > Output
- Select "Taxi Language Server" from dropdown

**Browser DevTools (for webview):**
- Help > Toggle Developer Tools
- Console shows webview logs

**Debug Console (for extension host):**
- View > Debug Console
- Shows extension-side logs

### Common Issues

**Operations not loading:**
- Check language server is running
- Look for "taxiql/listOperations endpoint not available" in console
- Verify language client connected: "Notebook components connected to language client"

**Stub editor doesn't open:**
- Check activation events in package.json
- Verify extension is activated
- Look for errors in Output panel

**Cell metadata not updating:**
- Ensure notebook is not read-only
- Check NotebookEdit.updateCellMetadata call succeeds
- View raw JSON (Open With > Text Editor) to verify

## Summary

This implementation demonstrates complete plumbing:

1. ✅ **UI Layer**: HTML webview with VSCode theming
2. ✅ **Communication**: Webview ↔ Extension via postMessage
3. ✅ **RPC**: Extension → Language Server via LSP
4. ✅ **Fallback**: Graceful handling when endpoint unavailable
5. ✅ **Integration**: Metadata updates in notebook cells

The stub data validates the infrastructure works end-to-end. Implementing the JVM endpoint is now straightforward - just add the `@JsonRequest` handler and parse the Taxi schema!
