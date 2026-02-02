import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import {
   ListOperationsRequest,
   ListOperationsResponse,
   ServiceMemberDto,
   OperationStub,
   GeneratePlaceholderRequest,
   GeneratePlaceholderResponse,
   TAXIQL_LIST_OPERATIONS,
   TAXIQL_GENERATE_PLACEHOLDER_STUB,
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
         case "getOperations":
            // Webview is ready or requesting operations, send initial data
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

         case "generatePlaceholder":
            // Generate placeholder stub for operation
            await this._generatePlaceholder(message.operationQualifiedName);
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
               type: "init",
               operations: [],
               stubs: this.existingStubs,
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
            type: "init",
            operations: response.operations,
            stubs: this.existingStubs,
         });
      } catch (error) {
         console.warn("Failed to load operations", error);
         // Send empty list if error
         this._panel.webview.postMessage({
            type: "init",
            operations: [],
            stubs: this.existingStubs,
         });
      }
   }

   private async _generatePlaceholder(operationQualifiedName: string) {
      try {
         if (!this.languageClient) {
            console.warn("Language client not available, cannot generate placeholder");
            return;
         }

         // Get workspace folder as project root
         const workspaceFolders = vscode.workspace.workspaceFolders;
         const projectRoot = workspaceFolders?.[0]?.uri.fsPath || "";

         const request: GeneratePlaceholderRequest = {
            operationQualifiedName,
            projectRoot,
         };

         const response = await this.languageClient.sendRequest<GeneratePlaceholderResponse>(
            TAXIQL_GENERATE_PLACEHOLDER_STUB,
            request
         );

         // Send the generated placeholder back to the webview
         this._panel.webview.postMessage({
            type: "placeholderGenerated",
            jsonStub: response.jsonStub,
         });
      } catch (error) {
         console.error("Failed to generate placeholder", error);
         vscode.window.showErrorMessage(
            `Failed to generate placeholder: ${error instanceof Error ? error.message : String(error)}`
         );
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
      const webview = this._panel.webview;

      // Get the URI for the bundled stub editor script
      const scriptUri = webview.asWebviewUri(
         vscode.Uri.joinPath(this._extensionUri, 'out', 'webview', 'stub-editor.js')
      );

      // Use a nonce to only allow specific scripts to be run
      const nonce = this.getNonce();

      return `<!DOCTYPE html>
<html lang="en">
<head>
   <meta charset="UTF-8">
   <meta name="viewport" content="width=device-width, initial-scale=1.0">
   <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; connect-src ${webview.cspSource}; img-src ${webview.cspSource} data:; font-src ${webview.cspSource};">
   <title>Configure Stubs</title>
   <style>
      body {
         margin: 0;
         padding: 20px;
         font-family: var(--vscode-font-family);
         font-size: var(--vscode-font-size);
         color: var(--vscode-foreground);
         background-color: var(--vscode-editor-background);
      }

      #root {
         width: 100%;
         height: 100%;
      }
   </style>
</head>
<body>
   <div id="root"></div>
   <script nonce="${nonce}" src="${scriptUri}"></script>
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
