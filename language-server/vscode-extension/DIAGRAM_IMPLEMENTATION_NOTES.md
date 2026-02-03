# Taxi Diagram Implementation Notes

## What's Been Implemented

### Backend (Kotlin)

1. **NotebookService.kt** - Added endpoint:
   ```kotlin
   @JsonRequest("taxiql/getDiagramData")
   fun getDiagramData(params: DiagramDataRequest): CompletableFuture<QueryPlanResponse>
   ```

2. **DiagramDataRequest** - Request type:
   ```kotlin
   data class DiagramDataRequest(
      val names: List<String>,  // Type/service names
      val projectRoot: String
   )
   ```

3. **TaxiNotebookService.kt** - Stub implementation:
   - Currently returns `QueryPlanResponse(diagramData = emptyMap<String, Any>())`
   - **TODO**: Implement actual diagram generation logic here

4. **TaxiLanguageServerWithNotebooks.kt** - Delegates to notebookService

### Frontend (TypeScript)

1. **notebookProtocol.ts** - Added:
   - `DiagramDataRequest` interface
   - `TAXIQL_GET_DIAGRAM_DATA` constant

2. **taxiDiagramPlugin.ts** - Markdown-it plugin:
   - Renders `taxi-diagram` code blocks as div with data attributes
   - Includes loading placeholder
   - Data attributes contain the names list

3. **diagramPreview.ts** - Markdown preview script:
   - Runs in markdown preview webview
   - Finds `taxi-diagram` elements
   - **TODO**: Wire up LSP call to fetch diagram data
   - **TODO**: Integrate query plan visualization component

4. **package.json** - Configuration:
   - Added markdown.previewScripts contribution
   - Added compile:diagram-preview script

## How It Works

1. User writes in markdown:
   ````markdown
   ```taxi-diagram
   Person
   PersonService
   com.foo.FirstName
   ```
   ````

2. Markdown-it plugin converts this to:
   ```html
   <div class="taxi-diagram-container"
        data-taxi-diagram="true"
        data-names='["Person","PersonService","com.foo.FirstName"]'>
      <div class="taxi-diagram-loading">Loading...</div>
      <div class="taxi-diagram-content" style="display:none;"></div>
   </div>
   ```

3. Markdown preview script (`diagramPreview.ts`) finds these elements

4. Script should call language server via: `TAXIQL_GET_DIAGRAM_DATA`

5. Render the QueryPlanResponse data using query plan visualization

## Next Steps

### Server-Side Implementation

In `TaxiNotebookService.getDiagramData()`:
- Parse the names from `params.names`
- Look up types/services in the compiled schema
- Generate diagram data (QueryPlanDiagramData format)
- Return as `QueryPlanResponse`

### Client-Side Integration

Two options:

**Option A: Static rendering** (simpler)
- Have markdown-it plugin call language server during rendering
- Embed diagram data in HTML
- Preview script just visualizes embedded data

**Option B: Dynamic fetching** (more complex but live updates)
- Preview script calls extension command
- Extension calls language server
- Extension posts message back to preview
- Preview renders diagram

**Option C: Use existing renderer**
- Load the notebook renderer bundle in markdown preview
- Reuse `QueryPlanVisualization` React component
- Need to bootstrap React in the preview context

## Current Status

✅ Backend endpoint defined (stub)
✅ Protocol types added
✅ Markdown-it plugin renders placeholders
✅ Preview script scaffolded
⚠️ Diagram data generation not implemented (returns empty map)
⚠️ LSP call from preview not wired up
⚠️ Query plan visualization not integrated

## Testing

1. Create a markdown file with a taxi-diagram block
2. Open markdown preview
3. You should see "Loading diagram..." placeholder
4. Once implementation is complete, diagram should render
