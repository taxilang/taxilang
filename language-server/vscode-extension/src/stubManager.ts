import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import { StubEditorPanel } from "./stubEditor";
import { OperationStub } from "./notebookProtocol";

/**
 * Manages stub attachments for TaxiQL notebook cells
 */
export class StubManager {
   private languageClient: LanguageClient | null = null;
   private extensionUri: vscode.Uri;

   constructor(extensionUri: vscode.Uri) {
      this.extensionUri = extensionUri;
   }

   setLanguageClient(client: LanguageClient) {
      this.languageClient = client;
   }
   /**
    * Attach stubs to a notebook cell
    */
   async attachStubs(cell?: vscode.NotebookCell): Promise<void> {
      const targetCell = cell || this.getActiveCell();
      if (!targetCell) {
         vscode.window.showErrorMessage("No active cell found");
         return;
      }

      // Get existing stubs (if any)
      const existingStubs = targetCell.metadata?.taxi?.stubs || [];

      // Open the stub editor webview with auto-save callback
      const stubs = await StubEditorPanel.show(
         this.extensionUri,
         this.languageClient,
         existingStubs,
         async (updatedStubs) => {
            // Auto-save stubs as they're edited
            await this.updateCellMetadata(targetCell, updatedStubs);
         }
      );

      // Stubs are already saved via the callback, just show confirmation
      if (stubs !== null) {
         vscode.window.showInformationMessage(
            `${stubs.length} stub(s) configured`
         );
      }
   }

   /**
    * Change stubs for a notebook cell
    */
   async changeStubs(cell?: vscode.NotebookCell): Promise<void> {
      const targetCell = cell || this.getActiveCell();
      if (!targetCell) {
         vscode.window.showErrorMessage("No active cell found");
         return;
      }

      // Get existing stubs (if any)
      const existingStubs = targetCell.metadata?.taxi?.stubs || [];

      // Open the stub editor webview with auto-save callback
      const stubs = await StubEditorPanel.show(
         this.extensionUri,
         this.languageClient,
         existingStubs,
         async (updatedStubs) => {
            // Auto-save stubs as they're edited
            await this.updateCellMetadata(targetCell, updatedStubs);
         }
      );

      // Stubs are already saved via the callback, just show confirmation
      if (stubs !== null) {
         vscode.window.showInformationMessage(
            `${stubs.length} stub(s) configured`
         );
      }
   }

   /**
    * Clear stubs from a notebook cell
    */
   async clearStubs(cell?: vscode.NotebookCell): Promise<void> {
      const targetCell = cell || this.getActiveCell();
      if (!targetCell) {
         vscode.window.showErrorMessage("No active cell found");
         return;
      }

      // Remove stubs from metadata
      await this.updateCellMetadata(targetCell, []);

      vscode.window.showInformationMessage("Stubs cleared from cell");
   }

   /**
    * Update cell metadata with stubs array
    */
   private async updateCellMetadata(
      cell: vscode.NotebookCell,
      stubs: OperationStub[]
   ): Promise<void> {
      console.log(`[StubManager] Updating cell metadata with ${stubs.length} stubs:`, JSON.stringify(stubs, null, 2));

      const edit = new vscode.WorkspaceEdit();
      const notebook = cell.notebook;
      const index = cell.index;

      // Get existing metadata
      const existingMetadata = cell.metadata || {};
      const taxiMetadata = existingMetadata.taxi || {};

      // Update taxi metadata with stubs
      const newTaxiMetadata = {
         ...taxiMetadata,
         stubs: stubs.length > 0 ? stubs : undefined,
      };

      // Remove stubs if empty array
      if (stubs.length === 0 && newTaxiMetadata.stubs) {
         delete newTaxiMetadata.stubs;
      }

      // Create new metadata object
      const newMetadata = {
         ...existingMetadata,
         taxi: newTaxiMetadata,
      };

      console.log('[StubManager] New metadata:', JSON.stringify(newMetadata, null, 2));

      // Apply the edit
      const notebookEdit = vscode.NotebookEdit.updateCellMetadata(
         index,
         newMetadata
      );

      edit.set(notebook.uri, [notebookEdit]);
      await vscode.workspace.applyEdit(edit);

      console.log('[StubManager] Metadata edit applied successfully');
   }

   /**
    * Get the currently active notebook cell
    */
   private getActiveCell(): vscode.NotebookCell | undefined {
      const editor = vscode.window.activeNotebookEditor;
      if (!editor) {
         return undefined;
      }

      const selection = editor.selection;
      if (!selection) {
         return undefined;
      }

      return editor.notebook.cellAt(selection.start);
   }

   /**
    * Register stub-related commands
    */
   static registerCommands(
      context: vscode.ExtensionContext,
      stubManager: StubManager
   ): void {
      context.subscriptions.push(
         vscode.commands.registerCommand(
            "taxiql.notebook.attachStubs",
            (cell?: vscode.NotebookCell) => stubManager.attachStubs(cell)
         ),
         vscode.commands.registerCommand(
            "taxiql.notebook.changeStubs",
            (cell?: vscode.NotebookCell) => stubManager.changeStubs(cell)
         ),
         vscode.commands.registerCommand(
            "taxiql.notebook.clearStubs",
            (cell?: vscode.NotebookCell) => stubManager.clearStubs(cell)
         )
      );
   }
}
