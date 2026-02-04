import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import {
   StubQueryRequest,
   StubQueryResponse,
   OperationStub,
   QueryPlanResponse,
   DiagramDataRequest,
   TAXIQL_EXECUTE_WITH_STUBS,
   TAXIQL_GENERATE_QUERY_PLAN,
   TAXIQL_GET_DIAGRAM_DATA,
} from "./notebookProtocol";

/**
 * Controller for executing TaxiQL notebook cells
 */
export class TaxiQLNotebookController {
   readonly controllerId = "taxiql-notebook-controller";
   readonly notebookType = "taxiql-notebook";
   readonly label = "TaxiQL";
   readonly supportedLanguages = ["taxi", "taxiql-stubs", "taxi-diagram", "markdown"];

   private readonly controller: vscode.NotebookController;
   private readonly outputChannel: vscode.OutputChannel;
   private executionOrder = 0;
   private languageClient: LanguageClient | null = null;

   constructor() {
      this.controller = vscode.notebooks.createNotebookController(
         this.controllerId,
         this.notebookType,
         this.label
      );

      this.controller.supportedLanguages = this.supportedLanguages;
      this.controller.supportsExecutionOrder = true;
      this.controller.executeHandler = this.execute.bind(this);

      // Create output channel for detailed error logging
      this.outputChannel = vscode.window.createOutputChannel("TaxiQL Errors");
   }

   setLanguageClient(client: LanguageClient) {
      this.languageClient = client;
   }

   private async execute(
      cells: vscode.NotebookCell[],
      _notebook: vscode.NotebookDocument,
      _controller: vscode.NotebookController
   ): Promise<void> {
      for (const cell of cells) {
         await this.executeCell(cell);
      }
   }

   private async executeCell(cell: vscode.NotebookCell): Promise<void> {
      const execution = this.controller.createNotebookCellExecution(cell);
      execution.executionOrder = ++this.executionOrder;
      execution.start(Date.now());

      // Clear any previous outputs (including errors) to avoid lingering messages
      execution.clearOutput();

      try {
         // Skip markdown cells
         if (cell.document.languageId === "markdown") {
            execution.end(true, Date.now());
            return;
         }

         // Handle taxi-diagram cells
         if (cell.document.languageId === "taxi-diagram") {
            await this.executeTaxiDiagramCell(cell, execution);
            return;
         }

         // Handle taxiql-stubs cells (just display info)
         if (cell.document.languageId === "taxiql-stubs") {
            const stubsData = cell.document.getText();
            try {
               const parsed = JSON.parse(stubsData);
               execution.replaceOutput([
                  new vscode.NotebookCellOutput([
                     vscode.NotebookCellOutputItem.text(
                        `Stubs registered with ID: ${parsed.id || "unknown"}`,
                        "text/plain"
                     ),
                  ]),
               ]);
            } catch (e) {
               execution.replaceOutput([
                  new vscode.NotebookCellOutput([
                     vscode.NotebookCellOutputItem.error({
                        name: "Invalid JSON",
                        message: "Failed to parse stubs JSON",
                     }),
                  ]),
               ]);
            }
            execution.end(true, Date.now());
            return;
         }

         // Execute TaxiQL query
         if (cell.document.languageId === "taxi") {
            await this.executeTaxiQLCell(cell, execution);
         }
      } catch (error) {
         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.error(error as Error),
            ]),
         ]);
         execution.end(false, Date.now());
      }
   }

   private async executeTaxiQLCell(
      cell: vscode.NotebookCell,
      execution: vscode.NotebookCellExecution
   ): Promise<void> {
      const query = cell.document.getText();
      const metadata = cell.metadata || {};

      console.log('[NotebookController] Cell metadata:', JSON.stringify(metadata, null, 2));

      // Get stubs from cell metadata (stored as array of OperationStub)
      const stubs: OperationStub[] = metadata.taxi?.stubs || [];

      console.log(`[NotebookController] Found ${stubs.length} stubs in cell metadata:`, JSON.stringify(stubs, null, 2));

      // Resolve project context
      const projectContext = await this.resolveProjectContext(cell.notebook.uri);

      try {
         // Execute with stubs (empty array if no stubs configured)
         const result = await this.executeWithStubs(query, stubs, projectContext);

         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.json(
                  result,
                  "application/vnd.taxi.results+json"
               ),
            ]),
         ]);
         execution.end(true, Date.now());
      } catch (error) {
         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.error({
                  name: "Execution Error",
                  message: error instanceof Error ? error.message : String(error),
               }),
            ]),
         ]);
         execution.end(false, Date.now());
      }
   }

   private async executeWithStubs(
      query: string,
      stubs: OperationStub[],
      projectContext: ProjectContext
   ): Promise<StubQueryResponse> {
      if (!this.languageClient) {
         throw new Error("Language client not initialized");
      }

      // Call the taxiql/executeWithStubs RPC endpoint
      const request: StubQueryRequest = {
         query,
         projectRoot: projectContext.projectRoot,
         notebookPath: projectContext.notebookPath,
         stubs,
         parameters: {},
      };

      console.log('[NotebookController] Sending request to backend:', JSON.stringify(request, null, 2));

      try {
         const result = await this.languageClient.sendRequest<StubQueryResponse>(
            TAXIQL_EXECUTE_WITH_STUBS,
            request
         );
         return result;
      } catch (error: any) {
         // Extract JSONRPC error details
         const errorMessage = error?.message || String(error);
         const errorData = error?.data; // This contains the server-side error details
         const errorCode = error?.code;

         // Log detailed error information to output channel
         const timestamp = new Date().toISOString();
         this.outputChannel.appendLine(`[${timestamp}] TaxiQL Execution Error`);
         this.outputChannel.appendLine(`Query: ${query.substring(0, 100)}${query.length > 100 ? '...' : ''}`);
         this.outputChannel.appendLine(`Message: ${errorMessage}`);
         if (errorCode !== undefined) {
            this.outputChannel.appendLine(`Code: ${errorCode}`);
         }
         if (errorData) {
            this.outputChannel.appendLine(`Details: ${JSON.stringify(errorData, null, 2)}`);
         }
         this.outputChannel.appendLine(`Full error: ${JSON.stringify(error, null, 2)}`);
         this.outputChannel.appendLine('---');

         // Also log to console for debugging
         console.error("Failed to execute TaxiQL query:", error);

         // Show user-friendly error notification with option to view details
         vscode.window.showErrorMessage(
            `TaxiQL execution failed: ${errorMessage}`,
            'Show Details'
         ).then(selection => {
            if (selection === 'Show Details') {
               this.outputChannel.show();
            }
         });

         // Re-throw to let the caller handle it properly
         throw error;
      }
   }

   private async resolveProjectContext(
      notebookUri: vscode.Uri
   ): Promise<ProjectContext> {
      const workspaceFolder = vscode.workspace.getWorkspaceFolder(notebookUri);
      const projectRoot = workspaceFolder?.uri.fsPath || "";
      const notebookPath = notebookUri.fsPath;

      return {
         projectRoot,
         notebookPath,
      };
   }

   /**
    * Execute a taxi-diagram cell
    */
   private async executeTaxiDiagramCell(
      cell: vscode.NotebookCell,
      execution: vscode.NotebookCellExecution
   ): Promise<void> {
      const content = cell.document.getText();

      // Parse the cell content - each line is a type/service name
      const names = content
         .split('\n')
         .map(line => line.trim())
         .filter(line => line && !line.startsWith('#')); // Filter out empty lines and comments

      console.log(`[NotebookController] Executing taxi-diagram cell with ${names.length} elements:`, names);

      if (names.length === 0) {
         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.text(
                  'No diagram elements specified. Add type or service names (one per line).',
                  'text/plain'
               ),
            ]),
         ]);
         execution.end(true, Date.now());
         return;
      }

      try {
         // Get workspace folder as project root
         const workspaceFolders = vscode.workspace.workspaceFolders;
         const projectRoot = workspaceFolders?.[0]?.uri.fsPath || "";

         if (!this.languageClient) {
            throw new Error('Language client not initialized');
         }

         const request: DiagramDataRequest = {
            names,
            projectRoot
         };

         const response = await this.languageClient.sendRequest<QueryPlanResponse>(
            TAXIQL_GET_DIAGRAM_DATA,
            request
         );

         console.log('[NotebookController] Received diagram data:', response);

         // Output the diagram data with the query plan mime type
         // The notebook renderer will handle displaying it
         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.json(
                  response,
                  "application/vnd.taxi.queryplan+json"
               ),
            ]),
         ]);
         execution.end(true, Date.now());

      } catch (error) {
         console.error('[NotebookController] Error executing taxi-diagram cell:', error);

         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.error({
                  name: "Diagram Error",
                  message: error instanceof Error ? error.message : String(error),
               }),
            ]),
         ]);
         execution.end(false, Date.now());
      }
   }

   /**
    * Generate and display a query plan for the given cell
    */
   async showQueryPlan(cell: vscode.NotebookCell): Promise<void> {
      if (!this.languageClient) {
         vscode.window.showErrorMessage("Language client not initialized");
         return;
      }

      const query = cell.document.getText();
      const projectContext = await this.resolveProjectContext(cell.notebook.uri);

      // Create a cell execution to show progress
      const execution = this.controller.createNotebookCellExecution(cell);
      execution.executionOrder = ++this.executionOrder;
      execution.start(Date.now());

      // Build the request (same as StubQueryRequest but for query plan)
      const request: StubQueryRequest = {
         query,
         projectRoot: projectContext.projectRoot,
         notebookPath: projectContext.notebookPath,
         stubs: [], // Query plan doesn't need stubs
         parameters: {},
      };

      try {
         console.log("Sending query plan request:", TAXIQL_GENERATE_QUERY_PLAN, request);

         // Call the language server to generate the query plan
         const response = await this.languageClient.sendRequest<QueryPlanResponse>(
            TAXIQL_GENERATE_QUERY_PLAN,
            request
         );

         console.log("Received query plan response:", response);

         // Replace cell outputs with the query plan visualization
         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.json(
                  response,
                  "application/vnd.taxi.queryplan+json"
               ),
            ]),
         ]);
         execution.end(true, Date.now());

      } catch (error) {
         console.error("Query plan error:", error);
         console.error("Error details:", JSON.stringify(error, null, 2));

         execution.replaceOutput([
            new vscode.NotebookCellOutput([
               vscode.NotebookCellOutputItem.error({
                  name: "Query Plan Error",
                  message: error instanceof Error ? error.message : String(error),
               }),
            ]),
         ]);
         execution.end(false, Date.now());
      }
   }

   dispose() {
      this.controller.dispose();
      this.outputChannel.dispose();
   }
}

interface ProjectContext {
   projectRoot: string;
   notebookPath: string;
}
