/**
 * Webview Panel for displaying Taxi Diagrams
 *
 * Singleton pattern - reuses the same panel and updates content.
 * Displays diagrams fetched from the language server.
 */

import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import { DiagramDataRequest, QueryPlanResponse, TAXIQL_GET_DIAGRAM_DATA } from '../notebookProtocol';

export class TaxiDiagramPanel {
   public static currentPanel: TaxiDiagramPanel | undefined;
   private readonly _panel: vscode.WebviewPanel;
   private readonly _extensionUri: vscode.Uri;
   private _disposables: vscode.Disposable[] = [];

   private constructor(
      panel: vscode.WebviewPanel,
      extensionUri: vscode.Uri,
      private languageClient: LanguageClient | null
   ) {
      this._panel = panel;
      this._extensionUri = extensionUri;

      // Set up message handling
      this._panel.webview.onDidReceiveMessage(
         (message) => this._handleMessage(message),
         null,
         this._disposables
      );

      // Handle panel disposal
      this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

      // Set initial HTML
      this._update();
   }

   /**
    * Show the diagram panel (singleton pattern)
    */
   public static async show(
      extensionUri: vscode.Uri,
      languageClient: LanguageClient | null,
      documentUri: vscode.Uri,
      startLine: number
   ): Promise<void> {
      const column = vscode.ViewColumn.Beside;

      // Reuse existing panel if available
      if (TaxiDiagramPanel.currentPanel) {
         TaxiDiagramPanel.currentPanel._panel.reveal(column);
      } else {
         // Create new panel
         const panel = vscode.window.createWebviewPanel(
            'taxiDiagram',
            'Taxi Diagram',
            column,
            {
               enableScripts: true,
               retainContextWhenHidden: true,
               localResourceRoots: [extensionUri]
            }
         );

         TaxiDiagramPanel.currentPanel = new TaxiDiagramPanel(panel, extensionUri, languageClient);
      }

      // Load and display the diagram
      await TaxiDiagramPanel.currentPanel._loadDiagram(documentUri, startLine);
   }

   /**
    * Load diagram data from the document and render it
    */
   private async _loadDiagram(documentUri: vscode.Uri, startLine: number): Promise<void> {
      try {
         // Show loading state
         this._panel.webview.postMessage({ type: 'loading' });

         // Read the document and extract diagram elements
         const document = await vscode.workspace.openTextDocument(documentUri);
         const elements = this._extractDiagramElements(document, startLine);

         if (elements.length === 0) {
            this._panel.webview.postMessage({
               type: 'error',
               message: 'No diagram elements found in the block'
            });
            return;
         }

         // Get workspace folder as project root
         const workspaceFolders = vscode.workspace.workspaceFolders;
         const projectRoot = workspaceFolders?.[0]?.uri.fsPath || "";

         // Call language server to get diagram data
         if (!this.languageClient) {
            throw new Error('Language client not available');
         }

         const request: DiagramDataRequest = {
            names: elements,
            projectRoot
         };

         const response = await this.languageClient.sendRequest<QueryPlanResponse>(
            TAXIQL_GET_DIAGRAM_DATA,
            request
         );

         // Send diagram data to webview
         this._panel.webview.postMessage({
            type: 'diagramData',
            data: response.diagramData,
            elements
         });

      } catch (error) {
         console.error('Failed to load diagram:', error);
         this._panel.webview.postMessage({
            type: 'error',
            message: `Failed to load diagram: ${error instanceof Error ? error.message : String(error)}`
         });
      }
   }

   /**
    * Extract diagram element names from the taxi-diagram block
    */
   private _extractDiagramElements(document: vscode.TextDocument, startLine: number): string[] {
      const elements: string[] = [];
      let currentLine = startLine + 1; // Skip the ```taxi-diagram line

      // Read lines until we hit the closing ```
      while (currentLine < document.lineCount) {
         const lineText = document.lineAt(currentLine).text.trim();

         // Check for closing fence
         if (lineText === '```' || lineText.startsWith('```')) {
            break;
         }

         // Add non-empty lines as elements
         if (lineText) {
            elements.push(lineText);
         }

         currentLine++;
      }

      return elements;
   }

   /**
    * Handle messages from the webview
    */
   private async _handleMessage(message: any) {
      switch (message.type) {
         case 'refresh':
            // TODO: Implement refresh functionality
            break;

         case 'error':
            vscode.window.showErrorMessage(`Diagram Error: ${message.message}`);
            break;
      }
   }

   /**
    * Update the webview HTML
    */
   private async _update() {
      this._panel.webview.html = this._getHtmlForWebview();
   }

   /**
    * Dispose the panel
    */
   public dispose() {
      TaxiDiagramPanel.currentPanel = undefined;

      this._panel.dispose();

      while (this._disposables.length) {
         const disposable = this._disposables.pop();
         if (disposable) {
            disposable.dispose();
         }
      }
   }

   /**
    * Generate HTML for the webview
    */
   private _getHtmlForWebview() {
      const webview = this._panel.webview;

      // Get the URI for the bundled renderer script (same as used for notebook rendering)
      const rendererUri = webview.asWebviewUri(
         vscode.Uri.joinPath(this._extensionUri, 'out', 'renderer', 'index.js')
      );

      // Use a nonce for security
      const nonce = this.getNonce();

      return `<!DOCTYPE html>
<html lang="en">
<head>
   <meta charset="UTF-8">
   <meta name="viewport" content="width=device-width, initial-scale=1.0">
   <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; connect-src ${webview.cspSource}; img-src ${webview.cspSource} data:; font-src ${webview.cspSource};">
   <title>Taxi Diagram</title>
   <style>
      body {
         margin: 0;
         padding: 0;
         font-family: var(--vscode-font-family);
         font-size: var(--vscode-font-size);
         color: var(--vscode-foreground);
         background-color: var(--vscode-editor-background);
         overflow: hidden;
      }

      #loading {
         display: flex;
         align-items: center;
         justify-content: center;
         height: 100vh;
         color: var(--vscode-descriptionForeground);
      }

      #error {
         display: none;
         padding: 16px;
         margin: 16px;
         background-color: var(--vscode-inputValidation-errorBackground);
         border: 1px solid var(--vscode-inputValidation-errorBorder);
         border-radius: 4px;
         color: var(--vscode-errorForeground);
      }

      #diagram-root {
         display: none;
         width: 100vw;
         height: 100vh;
      }

      /* Style for the query plan visualization */
      .react-flow {
         background-color: var(--vscode-editor-background);
      }

      .react-flow__node {
         background-color: var(--vscode-editor-background);
         border: 1px solid var(--vscode-panel-border);
         color: var(--vscode-foreground);
      }
   </style>
</head>
<body>
   <div id="loading">
      <div>⏳ Loading diagram...</div>
   </div>

   <div id="error"></div>

   <div id="diagram-root"></div>

   <script nonce="${nonce}">
      const vscode = acquireVsCodeApi();
      let diagramData = null;

      // Handle messages from extension
      window.addEventListener('message', event => {
         const message = event.data;

         switch (message.type) {
            case 'loading':
               document.getElementById('loading').style.display = 'flex';
               document.getElementById('error').style.display = 'none';
               document.getElementById('diagram-root').style.display = 'none';
               break;

            case 'error':
               document.getElementById('loading').style.display = 'none';
               document.getElementById('error').style.display = 'block';
               document.getElementById('error').textContent = message.message;
               document.getElementById('diagram-root').style.display = 'none';
               break;

            case 'diagramData':
               document.getElementById('loading').style.display = 'none';
               document.getElementById('error').style.display = 'none';
               document.getElementById('diagram-root').style.display = 'block';

               // Store diagram data for the renderer
               diagramData = {
                  diagramData: message.data,
                  elements: message.elements
               };

               // Trigger renderer to display the diagram
               renderDiagram();
               break;
         }
      });

      function renderDiagram() {
         if (!diagramData) return;

         // The renderer will be loaded below and will pick up diagramData
         const event = new CustomEvent('renderDiagram', { detail: diagramData });
         window.dispatchEvent(event);
      }
   </script>

   <!-- Load the renderer bundle (same as notebook renderer) -->
   <script type="module" nonce="${nonce}" src="${rendererUri}"></script>
</body>
</html>`;
   }

   private getNonce() {
      let text = '';
      const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
      for (let i = 0; i < 32; i++) {
         text += possible.charAt(Math.floor(Math.random() * possible.length));
      }
      return text;
   }
}
