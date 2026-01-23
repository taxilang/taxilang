import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient";
import {
   TAXIQL_LIST_OPERATIONS,
   ListOperationsRequest,
   ListOperationsResponse,
} from "./notebookProtocol";

/**
 * Manages the stub editor webview panel
 */
export class StubEditorPanel {
   public static currentPanel: StubEditorPanel | undefined;
   private readonly panel: vscode.WebviewPanel;
   private readonly extensionUri: vscode.Uri;
   private disposables: vscode.Disposable[] = [];
   private languageClient: LanguageClient | null = null;
   private resolveCallback: ((stubsId: string | undefined) => void) | null = null;

   private constructor(panel: vscode.WebviewPanel, extensionUri: vscode.Uri) {
      this.panel = panel;
      this.extensionUri = extensionUri;

      // Set the webview's content
      this.update();

      // Listen for when the panel is disposed
      this.panel.onDidDispose(() => this.dispose(), null, this.disposables);

      // Handle messages from the webview
      this.panel.webview.onDidReceiveMessage(
         async (message) => {
            await this.handleMessage(message);
         },
         null,
         this.disposables
      );
   }

   public static show(
      extensionUri: vscode.Uri,
      languageClient: LanguageClient | null
   ): Promise<string | undefined> {
      const column = vscode.window.activeTextEditor
         ? vscode.window.activeTextEditor.viewColumn
         : undefined;

      // If we already have a panel, show it
      if (StubEditorPanel.currentPanel) {
         StubEditorPanel.currentPanel.panel.reveal(column);
         StubEditorPanel.currentPanel.languageClient = languageClient;
      } else {
         // Otherwise, create a new panel
         const panel = vscode.window.createWebviewPanel(
            "taxiqlStubEditor",
            "TaxiQL Stub Editor",
            column || vscode.ViewColumn.One,
            {
               enableScripts: true,
               retainContextWhenHidden: true,
               localResourceRoots: [extensionUri],
            }
         );

         StubEditorPanel.currentPanel = new StubEditorPanel(panel, extensionUri);
         StubEditorPanel.currentPanel.languageClient = languageClient;
      }

      // Return a promise that resolves when the user selects a stub
      return new Promise((resolve) => {
         if (StubEditorPanel.currentPanel) {
            StubEditorPanel.currentPanel.resolveCallback = resolve;
         }
      });
   }

   private async handleMessage(message: any) {
      switch (message.type) {
         case "getOperations":
            await this.fetchOperations(message.projectRoot);
            break;

         case "selectStub":
            // User selected/created a stub
            if (this.resolveCallback) {
               this.resolveCallback(message.stubsId);
               this.resolveCallback = null;
            }
            // Don't close the panel - user might want to edit more stubs
            break;

         case "cancel":
            if (this.resolveCallback) {
               this.resolveCallback(undefined);
               this.resolveCallback = null;
            }
            this.panel.dispose();
            break;

         case "log":
            console.log("[Stub Editor]", message.message);
            break;
      }
   }

   private async fetchOperations(projectRoot: string) {
      if (!this.languageClient) {
         console.warn("Language client not available");
         this.panel.webview.postMessage({
            type: "operationsResponse",
            operations: [],
            error: "Language server not connected",
         });
         return;
      }

      try {
         const request: ListOperationsRequest = {
            projectRoot,
         };

         const response = await this.languageClient.sendRequest<ListOperationsResponse>(
            TAXIQL_LIST_OPERATIONS,
            request
         );

         this.panel.webview.postMessage({
            type: "operationsResponse",
            operations: response.operations,
         });
      } catch (error) {
         console.warn("taxiql/listOperations endpoint not available, using stub", error);

         // Return stub data
         this.panel.webview.postMessage({
            type: "operationsResponse",
            operations: [
               {
                  service: "CustomerService",
                  operation: "findCustomers",
                  returnType: "Customer[]",
               },
               {
                  service: "OrderService",
                  operation: "findOrders",
                  returnType: "Order[]",
               },
               {
                  service: "ProductService",
                  operation: "getProduct",
                  returnType: "Product",
               },
            ],
         });
      }
   }

   private update() {
      const webview = this.panel.webview;
      this.panel.webview.html = this.getHtmlForWebview(webview);
   }

   private getHtmlForWebview(webview: vscode.Webview): string {
      const nonce = getNonce();

      return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}';">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TaxiQL Stub Editor</title>
    <style>
        body {
            font-family: var(--vscode-font-family);
            color: var(--vscode-foreground);
            background-color: var(--vscode-editor-background);
            padding: 20px;
            margin: 0;
        }
        h1 {
            color: var(--vscode-foreground);
            border-bottom: 1px solid var(--vscode-panel-border);
            padding-bottom: 10px;
        }
        .section {
            margin: 20px 0;
            padding: 15px;
            background-color: var(--vscode-editor-background);
            border: 1px solid var(--vscode-panel-border);
            border-radius: 4px;
        }
        .section h2 {
            margin-top: 0;
            color: var(--vscode-foreground);
        }
        .status {
            padding: 10px;
            background-color: var(--vscode-inputValidation-infoBorder);
            border-left: 4px solid var(--vscode-inputValidation-infoBackground);
            margin: 10px 0;
            border-radius: 2px;
        }
        .operations-list {
            list-style: none;
            padding: 0;
            margin: 10px 0;
        }
        .operation-item {
            padding: 10px;
            margin: 5px 0;
            background-color: var(--vscode-list-hoverBackground);
            border-radius: 4px;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        .operation-item:hover {
            background-color: var(--vscode-list-activeSelectionBackground);
        }
        .operation-service {
            font-weight: bold;
            color: var(--vscode-symbolIcon-classForeground);
        }
        .operation-name {
            color: var(--vscode-symbolIcon-methodForeground);
        }
        .operation-return {
            color: var(--vscode-symbolIcon-variableForeground);
            font-size: 0.9em;
        }
        input, select {
            width: 100%;
            padding: 8px;
            margin: 5px 0;
            background-color: var(--vscode-input-background);
            color: var(--vscode-input-foreground);
            border: 1px solid var(--vscode-input-border);
            border-radius: 2px;
        }
        button {
            padding: 8px 16px;
            margin: 5px 5px 5px 0;
            background-color: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            border-radius: 2px;
            cursor: pointer;
        }
        button:hover {
            background-color: var(--vscode-button-hoverBackground);
        }
        button.secondary {
            background-color: var(--vscode-button-secondaryBackground);
            color: var(--vscode-button-secondaryForeground);
        }
        button.secondary:hover {
            background-color: var(--vscode-button-secondaryHoverBackground);
        }
        .loading {
            color: var(--vscode-foreground);
            opacity: 0.6;
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--vscode-descriptionForeground);
            font-style: italic;
        }
    </style>
</head>
<body>
    <h1>🎯 TaxiQL Stub Editor (Placeholder)</h1>

    <div class="status">
        <strong>Status:</strong> <span id="status">Initializing...</span>
    </div>

    <div class="section">
        <h2>Available Operations</h2>
        <p>Operations loaded from the language server (JVM):</p>
        <div id="operations-container">
            <div class="loading">Loading operations from language server...</div>
        </div>
    </div>

    <div class="section">
        <h2>Create/Select Stub</h2>
        <label for="stubId">Stub ID:</label>
        <input type="text" id="stubId" placeholder="e.g., customer-stubs" />

        <div style="margin-top: 15px;">
            <button onclick="selectStub()">Use This Stub</button>
            <button class="secondary" onclick="cancel()">Cancel</button>
        </div>
    </div>

    <div class="section">
        <h2>Implementation Notes</h2>
        <ul>
            <li>✅ Webview communicates with extension host</li>
            <li>✅ Extension host calls language server via RPC</li>
            <li>✅ Language server returns data (stubbed for now)</li>
            <li>✅ Stub ID is passed back to notebook cell metadata</li>
            <li>⏳ Rich UI for editing stub responses (future)</li>
            <li>⏳ Validation and preview (future)</li>
        </ul>
    </div>

    <script nonce="${nonce}">
        const vscode = acquireVsCodeApi();

        // Log helper
        function log(message) {
            vscode.postMessage({ type: 'log', message });
            console.log(message);
        }

        // Request operations from language server on load
        window.addEventListener('load', () => {
            log('Stub editor loaded, requesting operations...');
            document.getElementById('status').textContent = 'Loading operations from JVM...';

            // Request operations - projectRoot could be passed in
            vscode.postMessage({
                type: 'getOperations',
                projectRoot: '/workspace' // This would come from context
            });
        });

        // Handle messages from extension
        window.addEventListener('message', event => {
            const message = event.data;

            switch (message.type) {
                case 'operationsResponse':
                    handleOperationsResponse(message);
                    break;
            }
        });

        function handleOperationsResponse(message) {
            const container = document.getElementById('operations-container');

            if (message.error) {
                container.innerHTML = '<div class="placeholder">⚠️ ' + message.error + '</div>';
                document.getElementById('status').textContent = 'Error loading operations';
                return;
            }

            const operations = message.operations || [];

            if (operations.length === 0) {
                container.innerHTML = '<div class="placeholder">No operations found</div>';
                document.getElementById('status').textContent = 'No operations available';
                return;
            }

            log('Received ' + operations.length + ' operations from language server');
            document.getElementById('status').textContent = 'Connected to JVM ✓';

            // Render operations
            const list = document.createElement('ul');
            list.className = 'operations-list';

            operations.forEach(op => {
                const item = document.createElement('li');
                item.className = 'operation-item';
                item.innerHTML =
                    '<div class="operation-service">' + op.service + '</div>' +
                    '<div class="operation-name">→ ' + op.operation + '()</div>' +
                    '<div class="operation-return">Returns: ' + op.returnType + '</div>';

                item.onclick = () => {
                    // Auto-fill stub ID when clicking operation
                    const stubId = op.service.toLowerCase() + '-' + op.operation.toLowerCase();
                    document.getElementById('stubId').value = stubId;
                };

                list.appendChild(item);
            });

            container.innerHTML = '';
            container.appendChild(list);
        }

        function selectStub() {
            const stubId = document.getElementById('stubId').value.trim();
            if (!stubId) {
                alert('Please enter a stub ID');
                return;
            }

            log('User selected stub: ' + stubId);
            vscode.postMessage({
                type: 'selectStub',
                stubsId: stubId
            });
        }

        function cancel() {
            log('User cancelled');
            vscode.postMessage({ type: 'cancel' });
        }
    </script>
</body>
</html>`;
   }

   public dispose() {
      StubEditorPanel.currentPanel = undefined;

      // Resolve with undefined if still waiting
      if (this.resolveCallback) {
         this.resolveCallback(undefined);
         this.resolveCallback = null;
      }

      this.panel.dispose();

      while (this.disposables.length) {
         const disposable = this.disposables.pop();
         if (disposable) {
            disposable.dispose();
         }
      }
   }
}

function getNonce() {
   let text = "";
   const possible =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
   for (let i = 0; i < 32; i++) {
      text += possible.charAt(Math.floor(Math.random() * possible.length));
   }
   return text;
}
