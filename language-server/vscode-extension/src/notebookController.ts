import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient";
import {
   StubQueryRequest,
   StubQueryResponse,
   OperationStub,
   TAXIQL_EXECUTE_WITH_STUBS,
} from "./notebookProtocol";

/**
 * Controller for executing TaxiQL notebook cells
 */
export class TaxiQLNotebookController {
   readonly controllerId = "taxiql-notebook-controller";
   readonly notebookType = "taxiql-notebook";
   readonly label = "TaxiQL";
   readonly supportedLanguages = ["taxiql", "taxiql-stubs", "markdown"];

   private readonly controller: vscode.NotebookController;
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

      try {
         // Skip markdown cells
         if (cell.document.languageId === "markdown") {
            execution.end(true, Date.now());
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
         if (cell.document.languageId === "taxiql") {
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

      // Get stubs from cell metadata (stored as array of OperationStub)
      const stubs: OperationStub[] = metadata.taxi?.stubs || [];

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

      try {
         const result = await this.languageClient.sendRequest<StubQueryResponse>(
            TAXIQL_EXECUTE_WITH_STUBS,
            request
         );
         return result;
      } catch (error) {
         // If the endpoint doesn't exist yet, return a stub response
         console.warn(
            "taxiql/executeWithStubs endpoint not available, returning stub",
            error
         );
         return {
            data: [
               { id: 1, name: "Sample Row 1", value: 100 },
               { id: 2, name: "Sample Row 2", value: 200 },
            ],
            contentType: "application/json",
            metadata: {
               rowCount: 2,
               executionTime: "42ms",
               status: "stub",
            },
         };
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

   dispose() {
      this.controller.dispose();
   }
}

interface ProjectContext {
   projectRoot: string;
   notebookPath: string;
}
