import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient";
import {
   ListOperationsRequest,
   ListOperationsResponse,
   Operation,
   OperationStub,
   TAXIQL_LIST_OPERATIONS,
} from "./notebookProtocol";

/**
 * Webview panel for editing operation stubs
 */
export class StubEditorPanel {
   public static currentPanel: StubEditorPanel | undefined;
   private readonly _panel: vscode.WebviewPanel;
   private readonly _extensionUri: vscode.Uri;
   private _disposables: vscode.Disposable[] = [];
   private _resolveStubs?: (value: OperationStub[] | null) => void;

   private constructor(
      panel: vscode.WebviewPanel,
      extensionUri: vscode.Uri,
      private languageClient: LanguageClient | null,
      private existingStubs: OperationStub[]
   ) {
      this._panel = panel;
      this._extensionUri = extensionUri;

      // Set up message handling
      this._panel.webview.onDidReceiveMessage(
         (message) => this._handleMessage(message),
         null,
         this._disposables
      );

      // Handle when the panel is closed
      this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

      // Initialize the webview
      this._update();
   }

   /**
    * Show the stub editor and return the configured stubs
    */
   public static async show(
      extensionUri: vscode.Uri,
      languageClient: LanguageClient | null,
      existingStubs: OperationStub[] = []
   ): Promise<OperationStub[] | null> {
      const column = vscode.window.activeTextEditor
         ? vscode.window.activeTextEditor.viewColumn
         : undefined;

      // Reuse existing panel if it exists
      if (StubEditorPanel.currentPanel) {
         StubEditorPanel.currentPanel._panel.reveal(column);
         return new Promise((resolve) => {
            StubEditorPanel.currentPanel!._resolveStubs = resolve;
         });
      }

      // Create new panel
      const panel = vscode.window.createWebviewPanel(
         "taxiqlStubEditor",
         "Configure Stubs",
         column || vscode.ViewColumn.One,
         {
            enableScripts: true,
            retainContextWhenHidden: true,
            localResourceRoots: [extensionUri],
         }
      );

      const stubEditor = new StubEditorPanel(panel, extensionUri, languageClient, existingStubs);
      StubEditorPanel.currentPanel = stubEditor;

      return new Promise((resolve) => {
         stubEditor._resolveStubs = resolve;
      });
   }

   private async _handleMessage(message: any) {
      switch (message.type) {
         case "ready":
            // Webview is ready, send initial data
            await this._loadOperations();
            break;

         case "save":
            // User clicked save
            const stubs: OperationStub[] = message.stubs;
            if (this._resolveStubs) {
               this._resolveStubs(stubs);
            }
            this.dispose();
            break;

         case "cancel":
            // User clicked cancel
            if (this._resolveStubs) {
               this._resolveStubs(null);
            }
            this.dispose();
            break;

         case "error":
            vscode.window.showErrorMessage(`Stub Editor Error: ${message.message}`);
            break;
      }
   }

   private async _loadOperations() {
      try {
         if (!this.languageClient) {
            console.warn("Language client not available, using empty operations list");
            this._panel.webview.postMessage({
               type: "operations",
               operations: [],
               existingStubs: this.existingStubs,
            });
            return;
         }

         // Get workspace folder as project root
         const workspaceFolders = vscode.workspace.workspaceFolders;
         const projectRoot = workspaceFolders?.[0]?.uri.fsPath || "";

         const request: ListOperationsRequest = { projectRoot };

         const response = await this.languageClient.sendRequest<ListOperationsResponse>(
            TAXIQL_LIST_OPERATIONS,
            request
         );

         this._panel.webview.postMessage({
            type: "operations",
            operations: response.operations,
            existingStubs: this.existingStubs,
         });
      } catch (error) {
         console.warn("Failed to load operations", error);
         // Send empty list if error
         this._panel.webview.postMessage({
            type: "operations",
            operations: [],
            existingStubs: this.existingStubs,
         });
      }
   }

   private async _update() {
      this._panel.webview.html = this._getHtmlForWebview();
   }

   public dispose() {
      StubEditorPanel.currentPanel = undefined;

      this._panel.dispose();

      while (this._disposables.length) {
         const disposable = this._disposables.pop();
         if (disposable) {
            disposable.dispose();
         }
      }
   }

   private _getHtmlForWebview() {
      return `<!DOCTYPE html>
<html lang="en">
<head>
   <meta charset="UTF-8">
   <meta name="viewport" content="width=device-width, initial-scale=1.0">
   <title>Configure Stubs</title>
   <style>
      * {
         box-sizing: border-box;
         margin: 0;
         padding: 0;
      }

      body {
         font-family: var(--vscode-font-family);
         font-size: var(--vscode-font-size);
         color: var(--vscode-foreground);
         background-color: var(--vscode-editor-background);
         padding: 20px;
      }

      .container {
         max-width: 1200px;
         margin: 0 auto;
      }

      h1 {
         font-size: 24px;
         font-weight: 600;
         margin-bottom: 20px;
         color: var(--vscode-titleBar-activeForeground);
      }

      .toolbar {
         display: flex;
         gap: 12px;
         margin-bottom: 24px;
         padding-bottom: 16px;
         border-bottom: 1px solid var(--vscode-panel-border);
      }

      .btn {
         padding: 8px 16px;
         border: none;
         border-radius: 4px;
         cursor: pointer;
         font-size: 13px;
         font-weight: 500;
         transition: all 0.2s;
      }

      .btn-primary {
         background-color: var(--vscode-button-background);
         color: var(--vscode-button-foreground);
      }

      .btn-primary:hover {
         background-color: var(--vscode-button-hoverBackground);
      }

      .btn-secondary {
         background-color: var(--vscode-button-secondaryBackground);
         color: var(--vscode-button-secondaryForeground);
      }

      .btn-secondary:hover {
         background-color: var(--vscode-button-secondaryHoverBackground);
      }

      .btn-danger {
         background-color: var(--vscode-errorForeground);
         color: var(--vscode-editor-background);
      }

      .btn-danger:hover {
         opacity: 0.85;
      }

      .btn:disabled {
         opacity: 0.5;
         cursor: not-allowed;
      }

      .section {
         margin-bottom: 32px;
      }

      .section-title {
         font-size: 16px;
         font-weight: 600;
         margin-bottom: 12px;
         color: var(--vscode-descriptionForeground);
      }

      .operations-list {
         display: grid;
         gap: 12px;
         margin-bottom: 24px;
      }

      .operation-card {
         background-color: var(--vscode-sideBar-background);
         border: 1px solid var(--vscode-panel-border);
         border-radius: 6px;
         padding: 16px;
         cursor: pointer;
         transition: all 0.2s;
      }

      .operation-card:hover {
         background-color: var(--vscode-list-hoverBackground);
         border-color: var(--vscode-focusBorder);
      }

      .operation-header {
         display: flex;
         justify-content: space-between;
         align-items: center;
         margin-bottom: 8px;
      }

      .operation-name {
         font-weight: 600;
         font-size: 14px;
      }

      .operation-meta {
         display: flex;
         gap: 12px;
         font-size: 12px;
         color: var(--vscode-descriptionForeground);
      }

      .tag {
         background-color: var(--vscode-badge-background);
         color: var(--vscode-badge-foreground);
         padding: 2px 8px;
         border-radius: 3px;
         font-size: 11px;
         font-weight: 500;
      }

      .stub-list {
         display: grid;
         gap: 16px;
      }

      .stub-card {
         background-color: var(--vscode-sideBar-background);
         border: 1px solid var(--vscode-panel-border);
         border-radius: 6px;
         padding: 16px;
      }

      .stub-header {
         display: flex;
         justify-content: space-between;
         align-items: center;
         margin-bottom: 16px;
      }

      .stub-operation-name {
         font-weight: 600;
         font-size: 14px;
         color: var(--vscode-textLink-foreground);
      }

      .form-group {
         margin-bottom: 16px;
      }

      .form-label {
         display: block;
         margin-bottom: 6px;
         font-size: 13px;
         font-weight: 500;
         color: var(--vscode-input-foreground);
      }

      .form-input {
         width: 100%;
         padding: 8px 12px;
         background-color: var(--vscode-input-background);
         color: var(--vscode-input-foreground);
         border: 1px solid var(--vscode-input-border);
         border-radius: 4px;
         font-family: var(--vscode-editor-font-family);
         font-size: 13px;
      }

      .form-input:focus {
         outline: none;
         border-color: var(--vscode-focusBorder);
      }

      textarea.form-input {
         min-height: 120px;
         font-family: var(--vscode-editor-font-family);
         font-size: 12px;
         resize: vertical;
      }

      .form-checkbox {
         display: flex;
         align-items: center;
         gap: 8px;
      }

      .form-checkbox input {
         width: 16px;
         height: 16px;
      }

      .empty-state {
         text-align: center;
         padding: 48px 20px;
         color: var(--vscode-descriptionForeground);
      }

      .empty-state-icon {
         font-size: 48px;
         margin-bottom: 16px;
         opacity: 0.5;
      }

      .empty-state-title {
         font-size: 16px;
         font-weight: 600;
         margin-bottom: 8px;
      }

      .empty-state-description {
         font-size: 13px;
      }

      .stub-actions {
         display: flex;
         gap: 8px;
      }

      .icon-btn {
         padding: 6px 12px;
         border: none;
         background: transparent;
         color: var(--vscode-foreground);
         cursor: pointer;
         border-radius: 4px;
         font-size: 12px;
      }

      .icon-btn:hover {
         background-color: var(--vscode-toolbar-hoverBackground);
      }

      .search-box {
         position: relative;
         margin-bottom: 16px;
      }

      .search-input {
         width: 100%;
         padding: 8px 12px 8px 32px;
         background-color: var(--vscode-input-background);
         color: var(--vscode-input-foreground);
         border: 1px solid var(--vscode-input-border);
         border-radius: 4px;
         font-size: 13px;
      }

      .search-icon {
         position: absolute;
         left: 10px;
         top: 50%;
         transform: translateY(-50%);
         opacity: 0.5;
      }
   </style>
</head>
<body>
   <div class="container">
      <h1>Configure Operation Stubs</h1>

      <div class="toolbar">
         <button class="btn btn-primary" id="saveBtn">Save Stubs</button>
         <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
         <div style="flex: 1"></div>
         <span id="stubCount" style="align-self: center; color: var(--vscode-descriptionForeground); font-size: 13px;">0 stubs configured</span>
      </div>

      <div class="section">
         <div class="section-title">🎯 Configured Stubs</div>
         <div id="stubList" class="stub-list"></div>
         <div id="emptyStubState" class="empty-state">
            <div class="empty-state-icon">📝</div>
            <div class="empty-state-title">No stubs configured</div>
            <div class="empty-state-description">Add stubs from the available operations below</div>
         </div>
      </div>

      <div class="section">
         <div class="section-title">📚 Available Operations</div>
         <div class="search-box">
            <span class="search-icon">🔍</span>
            <input type="text" class="search-input" id="searchInput" placeholder="Search operations..." />
         </div>
         <div id="operationsList" class="operations-list"></div>
         <div id="loadingOperations" class="empty-state">
            <div class="empty-state-icon">⏳</div>
            <div class="empty-state-title">Loading operations...</div>
         </div>
      </div>
   </div>

   <script>
      const vscode = acquireVsCodeApi();

      let operations = [];
      let stubs = [];
      let filteredOperations = [];

      // Initialize
      window.addEventListener('load', () => {
         vscode.postMessage({ type: 'ready' });

         document.getElementById('saveBtn').addEventListener('click', saveStubs);
         document.getElementById('cancelBtn').addEventListener('click', cancel);
         document.getElementById('searchInput').addEventListener('input', filterOperations);
      });

      // Handle messages from extension
      window.addEventListener('message', event => {
         const message = event.data;

         switch (message.type) {
            case 'operations':
               operations = message.operations || [];
               stubs = message.existingStubs || [];
               filteredOperations = operations;
               render();
               break;
         }
      });

      function filterOperations(event) {
         const query = event.target.value.toLowerCase();
         filteredOperations = operations.filter(op =>
            op.service.toLowerCase().includes(query) ||
            op.operation.toLowerCase().includes(query) ||
            op.returnType.toLowerCase().includes(query)
         );
         renderOperations();
      }

      function render() {
         renderStubs();
         renderOperations();
         updateStubCount();
      }

      function renderStubs() {
         const stubList = document.getElementById('stubList');
         const emptyState = document.getElementById('emptyStubState');

         if (stubs.length === 0) {
            stubList.style.display = 'none';
            emptyState.style.display = 'block';
            return;
         }

         stubList.style.display = 'grid';
         emptyState.style.display = 'none';

         stubList.innerHTML = stubs.map((stub, index) => \`
            <div class="stub-card" data-index="\${index}">
               <div class="stub-header">
                  <div class="stub-operation-name">\${stub.operationName}</div>
                  <div class="stub-actions">
                     <button class="icon-btn" onclick="editStub(\${index})" title="Edit">✏️</button>
                     <button class="icon-btn" onclick="removeStub(\${index})" title="Remove">🗑️</button>
                  </div>
               </div>
               <div class="form-group">
                  <label class="form-label">Response JSON</label>
                  <textarea class="form-input" rows="4" readonly>\${stub.response}</textarea>
               </div>
               \${stub.echoInput ? '<div class="tag">Echo Input</div>' : ''}
            </div>
         \`).join('');
      }

      function renderOperations() {
         const operationsList = document.getElementById('operationsList');
         const loadingState = document.getElementById('loadingOperations');

         if (filteredOperations.length === 0) {
            operationsList.style.display = 'none';
            loadingState.innerHTML = \`
               <div class="empty-state-icon">🔍</div>
               <div class="empty-state-title">No operations found</div>
               <div class="empty-state-description">\${operations.length === 0 ? 'No operations available in schema' : 'Try a different search term'}</div>
            \`;
            loadingState.style.display = 'block';
            return;
         }

         operationsList.style.display = 'grid';
         loadingState.style.display = 'none';

         operationsList.innerHTML = filteredOperations.map(op => {
            const opName = op.service + '::' + op.operation;
            const hasStub = stubs.some(s => s.operationName === opName);
            return \`
               <div class="operation-card" onclick="addStubForOperation('\${escapeHtml(op.service)}', '\${escapeHtml(op.operation)}', '\${escapeHtml(op.returnType)}')">
                  <div class="operation-header">
                     <div class="operation-name">\${escapeHtml(op.service)}::\${escapeHtml(op.operation)}</div>
                     \${hasStub ? '<span class="tag">✓ Stubbed</span>' : '<span class="tag">+ Add Stub</span>'}
                  </div>
                  <div class="operation-meta">
                     <span>→ \${escapeHtml(op.returnType)}</span>
                     \${op.metadata?.httpMethod ? \`<span>\${escapeHtml(op.metadata.httpMethod)}</span>\` : ''}
                     \${op.metadata?.path ? \`<span>\${escapeHtml(op.metadata.path)}</span>\` : ''}
                  </div>
               </div>
            \`;
         }).join('');
      }

      function escapeHtml(unsafe) {
         return unsafe
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
      }

      function updateStubCount() {
         document.getElementById('stubCount').textContent = \`\${stubs.length} stub\${stubs.length !== 1 ? 's' : ''} configured\`;
      }

      window.addStubForOperation = function(service, operation, returnType) {
         const operationName = service + '::' + operation;

         // Check if stub already exists
         const existingIndex = stubs.findIndex(s => s.operationName === operationName);
         if (existingIndex !== -1) {
            editStub(existingIndex);
            return;
         }

         // Prompt for response JSON
         const response = prompt(\`Enter response JSON for \${operationName}:\`, \`{\\n  "example": "value"\\n}\`);

         if (response === null) return; // User cancelled

         try {
            // Validate JSON
            JSON.parse(response);

            stubs.push({
               operationName,
               response,
               echoInput: false,
               conditionalResponses: []
            });

            render();
         } catch (error) {
            alert('Invalid JSON: ' + error.message);
         }
      };

      window.editStub = function(index) {
         const stub = stubs[index];
         const response = prompt(\`Edit response JSON for \${stub.operationName}:\`, stub.response);

         if (response === null) return; // User cancelled

         try {
            // Validate JSON
            JSON.parse(response);
            stub.response = response;
            render();
         } catch (error) {
            alert('Invalid JSON: ' + error.message);
         }
      };

      window.removeStub = function(index) {
         if (confirm('Remove this stub?')) {
            stubs.splice(index, 1);
            render();
         }
      };

      function saveStubs() {
         vscode.postMessage({
            type: 'save',
            stubs: stubs
         });
      }

      function cancel() {
         vscode.postMessage({ type: 'cancel' });
      }
   </script>
</body>
</html>`;
   }
}
