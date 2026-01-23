# TaxiQL Notebook - Implementation TODO

## Project Overview

### Goal
Provide a first-class **VSCode notebook-based workflow** for authoring and executing **TaxiQL queries** with rich interactive outputs. Think Jupyter notebooks, but for TaxiQL.

### Use Cases
1. **Query Development**: Write and test TaxiQL queries interactively
2. **Query Plan Visualization**: See how queries will be executed before running them
3. **Stubbed Testing**: Execute queries against stubbed service responses for rapid iteration
4. **Documentation**: Mix queries with markdown documentation
5. **Future**: Execute against real services

### Architecture Decision Records

#### 1. Custom Notebook Format (`.taxiqnb`)
**Decision**: Use a custom notebook type instead of Jupyter notebooks
**Rationale**:
- No dependency on Jupyter infrastructure
- Full control over serialization format
- Cleaner integration with VSCode's native notebook API
- Simpler for users (just another file type)

#### 2. Stub References in Cell Metadata
**Decision**: Store stub references in cell metadata, not inline in queries
**Rationale**:
- Keeps queries clean and executable
- Allows same query to run with different stubs
- Enables UI-driven stub management
- Metadata schema: `{ "taxi": { "stubsId": "stubs-123" } }`

#### 3. Webview-Based Stub Editor
**Decision**: Use VSCode webview instead of native forms
**Rationale**:
- More flexibility for rich UI later
- Can reuse web technologies
- Demonstrates full plumbing: UI → Extension → JVM
- Better UX than simple input boxes

#### 4. RPC Integration with JVM
**Decision**: Extend existing language server with custom RPC endpoints
**Rationale**:
- Reuse existing JVM infrastructure and code
- Leverage LSP's JSON-RPC mechanism
- No need for separate process or HTTP server
- TypeScript ↔ Kotlin communication already working

#### 5. Graceful Fallbacks
**Decision**: Return stub data when JVM endpoints not implemented
**Rationale**:
- Allows frontend development without waiting for backend
- Better developer experience during implementation
- Progressive enhancement model
- Clear messaging about what's stubbed vs real

#### 6. Placeholder Renderers
**Decision**: Simple HTML renderers first, rich UI later
**Rationale**:
- Validate infrastructure works end-to-end
- Defer complexity until core is solid
- React Flow and table components are non-trivial
- Can iterate on UX separately

---

## Implementation Status

### ✅ Complete

#### Core Infrastructure
- [x] **Notebook Type Registration** (`package.json`)
  - Type: `taxiql-notebook`
  - File extension: `.taxiqnb`
  - Activation events configured
  - Menu contributions for "New File..."

- [x] **Notebook Serializer** (`src/notebookSerializer.ts`)
  - Reads/writes `.taxiqnb` files as JSON
  - Persists cell metadata (including stub references)
  - Persists notebook-level metadata
  - Supports code and markdown cells

- [x] **Notebook Controller** (`src/notebookController.ts`)
  - Executes TaxiQL cells
  - Executes taxiql-stubs cells (validation only)
  - Skips markdown cells
  - Determines execution mode based on stub attachment
  - Integrates with language client for RPC
  - Project context resolution

- [x] **Language Client Integration** (`src/extension.ts`)
  - Document selectors for notebook cells:
    - `{ scheme: "vscode-notebook-cell", language: "taxiql" }`
    - `{ scheme: "vscode-notebook-cell", language: "taxiql-stubs" }`
  - LSP features work in notebook cells (completion, hover, etc.)
  - Controller and stub manager connected to language client

#### Stub Management
- [x] **Stub Manager** (`src/stubManager.ts`)
  - Attach stubs command
  - Change stubs command
  - Clear stubs command
  - Cell metadata updates
  - Language client integration

- [x] **Stub Editor Webview** (`src/stubEditor.ts`)
  - HTML/CSS UI with VSCode theming
  - Lists operations from language server
  - Auto-fill stub IDs on operation click
  - Returns stub ID to cell metadata
  - Comprehensive error handling (see below)

- [x] **Commands** (`package.json`)
  - `taxiql.notebook.new` - Create new notebook
  - `taxiql.notebook.attachStubs` - Attach stubs to cell
  - `taxiql.notebook.changeStubs` - Change cell's stubs
  - `taxiql.notebook.clearStubs` - Remove stubs from cell

- [x] **Menus**
  - `file/newFile` - "New TaxiQL Notebook" in File menu
  - `notebook/cell/title` - Attach/Change/Clear stubs in cell toolbar

#### RPC Protocol
- [x] **Protocol Definitions** (`src/notebookProtocol.ts`)
  - `taxiql/plan` - Generate query execution plan
  - `taxiql/executeWithStubs` - Execute query with stubs
  - `taxiql/listOperations` - List operations from Taxi schema
  - Full TypeScript interfaces documented
  - Constants for endpoint names

- [x] **RPC Calls in Controller**
  - `showPlan()` calls `taxiql/plan`
  - `executeWithStubs()` calls `taxiql/executeWithStubs`
  - Both return stub data when endpoints not implemented

- [x] **RPC Calls in Stub Editor**
  - `fetchOperations()` calls `taxiql/listOperations`
  - Returns stub operations when endpoint not implemented

#### Renderers
- [x] **Notebook Renderer** (`src/renderer/index.ts`)
  - Handles `application/vnd.taxi.plan+json`
  - Handles `application/vnd.taxi.results+json`
  - Placeholder HTML with styled output
  - Validates HTML rendering works
  - Displays JSON data in formatted view
  - Shows metadata

#### Error Handling
- [x] **Comprehensive Error Handling** (see `ERROR_HANDLING.md`)
  - 10-second timeout on RPC requests
  - Language server connection failures
  - Endpoint not implemented
  - Invalid response formats
  - Webview JavaScript errors
  - Malformed messages
  - UI interaction errors
  - Always falls back to stub data
  - User-friendly error messages
  - Detailed console logging

#### Documentation
- [x] **NOTEBOOK_README.md** - Complete notebook documentation
  - Features overview
  - Testing instructions
  - RPC protocol details
  - Architecture diagrams
  - Sample notebook structure
  - Troubleshooting guide

- [x] **STUB_EDITOR_GUIDE.md** - Stub editor architecture
  - Communication flow diagrams
  - RPC protocol specification
  - JVM implementation guide
  - Testing procedures

- [x] **ERROR_HANDLING.md** - Error handling documentation
  - All error scenarios
  - Error flow diagrams
  - Testing procedures
  - Recovery mechanisms

- [x] **sample.taxiqnb** - Example notebook demonstrating features

#### Build & Configuration
- [x] TypeScript compilation working
- [x] tsconfig.json includes DOM types
- [x] package.json dependencies up to date
- [x] VSCode engine version bumped to 1.68.0+

---

## 🚧 Outstanding Work

### High Priority - Backend (JVM/Kotlin)

These are the main items needed to move from stub data to real functionality:

#### 1. Implement `taxiql/listOperations` Endpoint
**Location**: `taxi-lang-service/src/main/java/lang/taxi/lsp/TaxiLanguageServer.kt` (or similar)

**What it does**: Returns list of operations available in the Taxi schema

**Interface**:
```kotlin
@JsonRequest("taxiql/listOperations")
fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse>

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
```

**Implementation Steps**:
1. Load Taxi schema from `projectRoot`
2. Iterate through services and their operations
3. Extract service name, operation name, return type
4. Optionally include HTTP method, path, parameters in metadata
5. Return as `ListOperationsResponse`

**Current State**: Stub data returned in extension (3 hardcoded operations)

**Testing**: Once implemented, stub editor will automatically show real operations

---

#### 2. Implement `taxiql/plan` Endpoint
**Location**: Same as above

**What it does**: Generates a query execution plan showing how a TaxiQL query will be executed

**Interface**:
```kotlin
@JsonRequest("taxiql/plan")
fun planQuery(params: PlanRequest): CompletableFuture<PlanResponse>

data class PlanRequest(
    val query: String,
    val projectRoot: String,
    val notebookPath: String
)

data class PlanResponse(
    val nodes: List<PlanNode>,
    val edges: List<PlanEdge>,
    val metadata: Map<String, Any>? = null
)

data class PlanNode(
    val id: String,
    val label: String,
    val type: String,
    val metadata: Map<String, Any>? = null
)

data class PlanEdge(
    val from: String,
    val to: String,
    val label: String? = null
)
```

**Implementation Steps**:
1. Parse TaxiQL query
2. Generate execution plan (query planner logic)
3. Convert plan to graph structure (nodes + edges)
4. Return as `PlanResponse`

**Current State**: Stub data returned (3 nodes, 2 edges)

**Output**: Displayed in notebook cell with placeholder HTML

**Future**: Rich React Flow visualization will render the graph

---

#### 3. Implement `taxiql/executeWithStubs` Endpoint
**Location**: Same as above

**What it does**: Executes a TaxiQL query using stubbed service responses

**Interface**:
```kotlin
@JsonRequest("taxiql/executeWithStubs")
fun executeWithStubs(params: ExecuteWithStubsRequest): CompletableFuture<ExecuteWithStubsResponse>

data class ExecuteWithStubsRequest(
    val query: String,
    val stubsId: String,
    val projectRoot: String,
    val notebookPath: String
)

data class ExecuteWithStubsResponse(
    val data: Any,
    val metadata: ExecutionMetadata
)

data class ExecutionMetadata(
    val stubsId: String,
    val rowCount: Int?,
    val executionTime: String?,
    val status: String,
    val warnings: List<String>? = null,
    val trace: List<String>? = null
)
```

**Implementation Steps**:
1. Parse TaxiQL query
2. Load stub data for `stubsId` (from where? TBD)
3. Execute query against stubbed responses
4. Return results with metadata

**Current State**: Stub data returned (2 sample rows)

**Output**: Displayed in notebook cell with placeholder HTML

**Open Questions**:
- Where are stubs stored? (File system? Database? In-memory?)
- How are stubs loaded by ID?
- Stub format/schema?

---

### Medium Priority - Frontend (TypeScript)

#### 4. Rich Query Plan Visualization
**Location**: `src/renderer/index.ts` or new file

**What it does**: Renders query plans as interactive React Flow diagrams

**Current State**: Placeholder HTML showing JSON

**Technology Options**:
- [React Flow](https://reactflow.dev/) - Most popular, feature-rich
- [Cytoscape.js](https://js.cytoscape.org/) - Lighter, graph-focused
- [vis.js Network](https://visjs.org/) - Simpler, fewer dependencies

**Implementation**:
- Bundle React + React Flow in renderer
- Convert `PlanResponse` to React Flow nodes/edges
- Add interactivity:
  - Click node → jump to definition in Taxi file
  - Hover → show metadata tooltip
  - Zoom/pan controls
  - Layout algorithms (hierarchical, force-directed)

**Estimate**: 2-3 days for basic version

---

#### 5. Rich Results Visualization
**Location**: `src/renderer/index.ts` or new file

**What it does**: Renders query results as interactive tables/JSON viewers

**Current State**: Placeholder HTML showing JSON

**Features Needed**:
- **Table view** for array results
  - Sortable columns
  - Filterable
  - Pagination for large datasets
  - Copy cells
- **JSON tree view** for object/nested results
  - Collapsible nodes
  - Syntax highlighting
  - Copy paths
- **Raw view** for debugging
- **Tabs** to switch between views

**Technology Options**:
- [AG Grid](https://www.ag-grid.com/) - Feature-rich table (heavy)
- [react-json-view](https://www.npmjs.com/package/react-json-view) - JSON viewer
- Custom with VSCode's Codicon icons

**Estimate**: 2-4 days for basic version

---

#### 6. Full Stub Editor UI
**Location**: `src/stubEditor.ts` (expand existing webview)

**What it does**: Rich UI for creating/editing stub responses

**Current State**: Basic webview with operation list + stub ID input

**Features Needed**:
- **Stub Library**
  - List all available stubs
  - Search/filter stubs
  - View stub details
  - Delete stubs
- **Stub Creator**
  - Select operation
  - Define response data (JSON editor)
  - Set response status (success/error)
  - Add multiple responses per stub
- **Stub Validator**
  - Validate against operation return type
  - Show type mismatches
  - Preview what query will receive
- **Persistence**
  - Save stubs (where? File system?)
  - Load stubs by ID
  - Export/import stubs

**Open Questions**:
- How/where are stubs persisted?
- Stub format/schema?
- Should stubs be scoped per project? Per user?

**Estimate**: 3-5 days for full version

---

#### 7. Cell Status Bar Items
**Location**: New file `src/cellStatusBar.ts`

**What it does**: Shows stub status in cell footer

**Current State**: Not implemented (stubs visible only via commands)

**Implementation**:
```typescript
// Register cell status bar provider
vscode.notebooks.registerNotebookCellStatusBarItemProvider(
    "taxiql-notebook",
    new StubStatusBarProvider()
);

// Show items like:
// • "Stubs: none (Attach...)" - clickable
// • "Stubs: customer-stubs (Change...)" - clickable
```

**Estimate**: 1 day

---

### Low Priority - Enhancements

#### 8. Real Service Execution
**What it does**: Execute queries against real services (not stubs)

**Current State**: Not implemented

**Requires**:
- New RPC endpoint: `taxiql/execute` (no stubs)
- Service configuration (where are services?)
- Authentication handling
- Timeout handling
- Error handling for service failures

**Estimate**: 3-5 days + depends on Vyne/service architecture

---

#### 9. Auto-Complete for Stub IDs
**What it does**: Suggest existing stub IDs when attaching stubs

**Current State**: User types free-form text

**Implementation**:
- Add RPC endpoint: `taxiql/listStubs`
- Show QuickPick with existing stubs
- Filter as user types

**Estimate**: 1 day

---

#### 10. Inline Stub Preview
**What it does**: Show stub data inline below query cell

**Current State**: No preview

**Implementation**:
- Add "Preview Stubs" command
- Fetch stub data by ID
- Display in read-only JSON editor below cell

**Estimate**: 1-2 days

---

#### 11. Query Plan Node Interaction
**What it does**: Click plan nodes to navigate to Taxi definitions

**Current State**: Not implemented

**Requires**:
- Rich plan visualization (#4)
- Node metadata includes source locations
- Message passing from renderer to extension
- Use `vscode.window.showTextDocument()` to open files

**Estimate**: 1 day (after #4 is done)

---

## File Structure

```
vscode-extension/
├── src/
│   ├── extension.ts                   # Main entry point
│   ├── notebookSerializer.ts          # .taxiqnb file I/O
│   ├── notebookController.ts          # Cell execution
│   ├── notebookProtocol.ts            # RPC protocol definitions
│   ├── stubManager.ts                 # Stub commands
│   ├── stubEditor.ts                  # Webview stub editor
│   └── renderer/
│       └── index.ts                   # Notebook output renderer
│
├── out/                               # Compiled JavaScript
│   ├── extension.js
│   ├── notebookSerializer.js
│   ├── notebookController.js
│   ├── notebookProtocol.js
│   ├── stubManager.js
│   ├── stubEditor.js
│   └── renderer/
│       └── index.js
│
├── package.json                       # Extension manifest
├── tsconfig.json                      # TypeScript config
│
├── NOTEBOOK_README.md                 # User-facing docs
├── STUB_EDITOR_GUIDE.md              # Architecture docs
├── ERROR_HANDLING.md                 # Error handling docs
├── TODO.md                           # This file
└── sample.taxiqnb                    # Example notebook
```

---

## Testing Strategy

### Manual Testing Checklist

#### Notebook Basics
- [ ] Create new notebook via File > New File... > TaxiQL Notebook
- [ ] Add markdown cell and edit content
- [ ] Add TaxiQL query cell
- [ ] Add taxiql-stubs cell with JSON
- [ ] Save notebook as `.taxiqnb` file
- [ ] Close and reopen notebook (verify persistence)
- [ ] Cell metadata preserved after save/load

#### Execution
- [ ] Execute TaxiQL cell without stubs (see plan output)
- [ ] Execute TaxiQL cell with stubs (see results output)
- [ ] Execute taxiql-stubs cell (see validation message)
- [ ] Execute markdown cell (no-op)
- [ ] Execution order increments correctly

#### Stub Management
- [ ] Click "Attach Stubs..." button in cell toolbar
- [ ] Stub editor opens
- [ ] See operations list (stub data currently)
- [ ] Click operation to auto-fill stub ID
- [ ] Enter custom stub ID
- [ ] Click "Use This Stub"
- [ ] Cell metadata updated
- [ ] See "Change Stubs..." button appear
- [ ] Click "Change Stubs..." to modify
- [ ] Click "Clear Stubs" to remove

#### Error Handling
- [ ] Stop language server, open stub editor (see fallback)
- [ ] Open stub editor with slow server (timeout after 10s)
- [ ] Break webview JS (see global error handler)
- [ ] Invalid cell metadata (graceful handling)

#### LSP Integration
- [ ] Open TaxiQL cell in notebook
- [ ] Type query (autocomplete works)
- [ ] Hover over identifier (hover info works)
- [ ] Errors/warnings displayed
- [ ] Same LSP features as regular `.taxi` files

### Automated Testing (Future)

**Unit Tests** (`src/test/suite/`):
- [ ] NotebookSerializer serialization/deserialization
- [ ] StubManager cell metadata updates
- [ ] Protocol request/response validation

**Integration Tests**:
- [ ] End-to-end notebook creation → execution → output
- [ ] Stub attachment → execution with stubs
- [ ] Error scenarios

---

## Dependencies

### Runtime
- `vscode`: ^1.68.0 (API for notebooks)
- `vscode-languageclient`: 5.1.1 (LSP communication)
- `find-java-home`: ^1.1.0 (Java detection)
- `ws`: ^6.0.0 (WebSockets)
- `glob`: ^7.1.6 (File patterns)

### Development
- `typescript`: (TypeScript compilation)
- `@types/vscode`: ^1.68.0 (VSCode API types)
- `@types/node`: (Node.js types)
- `eslint`: (Linting)
- `vscode-test`: (Testing framework)

### Future (for rich renderers)
- `react`: ^18.x
- `react-dom`: ^18.x
- `react-flow`: ^11.x (or cytoscape.js, vis.js)
- `react-json-view`: ^1.x (or alternative)
- Bundler for renderer (webpack/esbuild/vite)

---

## Known Issues / Tech Debt

### 1. Stub Storage Not Defined
**Issue**: No decision on where/how stubs are persisted
**Impact**: Can't implement full stub editor or executeWithStubs
**Options**:
- File system (`.taxiqnb.stubs` adjacent files?)
- Workspace state
- Global storage
- External service

### 2. Project Context Resolution Simplistic
**Issue**: Uses workspace folder as project root
**Impact**: Multi-root workspaces, monorepos may not work correctly
**Fix**: Smarter project detection (look for `taxi.conf`, etc.)

### 3. No Caching of Operations
**Issue**: Fetches operations on every stub editor open
**Impact**: Slower UX, more RPC calls
**Fix**: Cache operations in extension, invalidate on schema changes

### 4. Renderer Bundling Not Optimized
**Issue**: Renderer is single JS file, no module bundling
**Impact**: Will be problematic when adding React/React Flow
**Fix**: Set up webpack/esbuild for renderer bundle

### 5. No Telemetry/Analytics
**Issue**: No visibility into usage, errors in the wild
**Impact**: Hard to prioritize improvements
**Fix**: Add telemetry (respecting user settings)

### 6. Error Messages Could Be More Actionable
**Issue**: Errors tell what's wrong, not how to fix
**Impact**: User frustration
**Fix**: Add "Learn More" links, suggested actions

---

## Development Workflow

### Setup
```bash
cd language-server/vscode-extension
npm install --ignore-scripts  # keytar build issues
npm run compile
```

### Development
```bash
npm run watch  # Auto-recompile on file changes
```

In VSCode:
- Press `F5` to launch Extension Development Host
- Make changes to TypeScript
- Reload extension host: `Ctrl+R` / `Cmd+R`

### Testing
```bash
npm test  # Run automated tests (when added)
```

Manual testing:
- Open Extension Development Host
- File > New File... > TaxiQL Notebook
- Test features per checklist above

### Debugging

**Extension Host**:
- Set breakpoints in TypeScript
- Check Debug Console for logs

**Webview (Stub Editor)**:
- Help > Toggle Developer Tools
- Check Console for logs
- Inspect Elements to debug CSS/HTML

**Language Server**:
- Output panel: View > Output > "Taxi Language Server"
- Enable debug logging in `taxi.conf`

---

## Handoff Notes

### For Another Developer/Agent

**Start Here**:
1. Read `NOTEBOOK_README.md` for user-facing overview
2. Read `STUB_EDITOR_GUIDE.md` for architecture understanding
3. Skim `ERROR_HANDLING.md` for error handling patterns
4. Review this `TODO.md` for what's done vs. outstanding

**Quick Start**:
```bash
cd language-server/vscode-extension
npm install --ignore-scripts
npm run compile
# Press F5 in VSCode to launch
```

**To Add JVM Endpoints** (highest priority):
1. Go to `taxi-lang-service/src/main/java/lang/taxi/lsp/`
2. Find `TaxiLanguageServer.kt` or similar
3. Add `@JsonRequest` handlers (see TODO items #1, #2, #3 above)
4. Refer to `src/notebookProtocol.ts` for TypeScript interfaces
5. Test by opening stub editor or executing cells

**To Add Rich Renderers** (medium priority):
1. Set up bundler (webpack/esbuild) for `src/renderer/`
2. Add React + React Flow dependencies
3. Update `src/renderer/index.ts` to use React
4. Convert `PlanResponse` to React Flow graph
5. See TODO items #4 and #5 above

**Code Style**:
- Use TypeScript's strict mode (already enabled)
- Prefer `async/await` over callbacks
- Always handle errors gracefully (never crash)
- Log errors to console for debugging
- User-facing messages should be friendly, not technical

**Testing**:
- Test with language server stopped (error paths)
- Test with slow network (timeouts)
- Test invalid data (validation)
- Test all user flows (see checklist)

---

## Questions for Product/Design

1. **Stub Storage**: Where should stubs be persisted? File system? Database? Workspace vs. global?
2. **Stub Scope**: Are stubs per-user, per-project, or shared across a team?
3. **Real Service Execution**: When executing against real services, how are services discovered? How is auth handled?
4. **Query Plan Detail Level**: How much detail should the plan visualization show? Just service calls, or include transformations, filters, etc.?
5. **Results Pagination**: For large result sets, should we paginate? What's a reasonable page size?
6. **Multi-Root Workspaces**: How should project context work with multi-root workspaces? Let user select?

---

## Success Criteria

### MVP (Minimum Viable Product)
- [x] Create/open TaxiQL notebooks
- [x] Write TaxiQL queries in cells
- [ ] Execute queries (see plans or results) ← **Blocked on JVM endpoints**
- [x] Attach stubs to queries
- [x] Graceful error handling
- [x] Basic output rendering

### V1 (Full Feature Set)
- [ ] Rich query plan visualization
- [ ] Rich results visualization
- [ ] Full stub editor with creation/editing
- [ ] Execute against real services
- [ ] Cell status bar items
- [ ] Auto-complete for stub IDs

### V2 (Polish & Enhancements)
- [ ] Inline stub preview
- [ ] Query plan node navigation
- [ ] Export notebooks to HTML/PDF
- [ ] Share notebooks with team
- [ ] Query history
- [ ] Saved queries / snippets

---

## Timeline Estimates

**Assuming one developer working full-time:**

- **JVM Endpoints** (items #1, #2, #3): 3-5 days
- **Rich Visualizations** (items #4, #5): 4-7 days
- **Full Stub Editor** (item #6): 3-5 days
- **Polish & Testing**: 2-3 days

**Total: 12-20 days** (2.5 - 4 weeks)

**Critical Path**: JVM endpoints must be done first, then everything else can be parallelized.

---

## Resources

### Documentation
- [VSCode Notebook API](https://code.visualstudio.com/api/extension-guides/notebook)
- [VSCode Webview API](https://code.visualstudio.com/api/extension-guides/webview)
- [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
- [React Flow Docs](https://reactflow.dev/learn)

### Example Repos
- [VSCode Notebook Samples](https://github.com/microsoft/vscode-extension-samples/tree/main/notebook-extend-markdown-renderer-sample)
- [Jupyter Extension](https://github.com/microsoft/vscode-jupyter) (for inspiration)

### Internal Docs
- `NOTEBOOK_README.md` - User guide
- `STUB_EDITOR_GUIDE.md` - Architecture
- `ERROR_HANDLING.md` - Error handling
- `src/notebookProtocol.ts` - RPC protocol

---

## Contact / Questions

If you're picking this up and have questions:
- Check existing documentation first (files above)
- Look at code comments (comprehensive)
- Test in Extension Development Host to see current behavior
- Check console logs (Extension Host + Browser DevTools for webview)

The code is well-structured and should be self-explanatory. Good luck! 🚀
