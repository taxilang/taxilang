import * as vscode from "vscode";

/**
 * Manages stub attachments for TaxiQL notebook cells
 */
export class StubManager {
   /**
    * Attach stubs to a notebook cell
    */
   async attachStubs(cell?: vscode.NotebookCell): Promise<void> {
      const targetCell = cell || this.getActiveCell();
      if (!targetCell) {
         vscode.window.showErrorMessage("No active cell found");
         return;
      }

      // For now, we'll use a simple input box to get the stubs ID
      // In the future, this will open a web-based stub editor UI
      const stubsId = await vscode.window.showInputBox({
         prompt: "Enter Stubs ID",
         placeHolder: "e.g., stubs-123",
         validateInput: (value) => {
            if (!value || value.trim().length === 0) {
               return "Stubs ID cannot be empty";
            }
            return null;
         },
      });

      if (!stubsId) {
         return;
      }

      // Update cell metadata
      await this.updateCellMetadata(targetCell, stubsId);

      vscode.window.showInformationMessage(
         `Stubs "${stubsId}" attached to cell`
      );
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

      const currentStubsId = targetCell.metadata?.taxi?.stubsId;
      const newStubsId = await vscode.window.showInputBox({
         prompt: "Enter new Stubs ID",
         placeHolder: "e.g., stubs-456",
         value: currentStubsId,
         validateInput: (value) => {
            if (!value || value.trim().length === 0) {
               return "Stubs ID cannot be empty";
            }
            return null;
         },
      });

      if (!newStubsId) {
         return;
      }

      // Update cell metadata
      await this.updateCellMetadata(targetCell, newStubsId);

      vscode.window.showInformationMessage(
         `Stubs changed to "${newStubsId}"`
      );
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
      await this.updateCellMetadata(targetCell, undefined);

      vscode.window.showInformationMessage("Stubs cleared from cell");
   }

   /**
    * Update cell metadata with stubs ID
    */
   private async updateCellMetadata(
      cell: vscode.NotebookCell,
      stubsId: string | undefined
   ): Promise<void> {
      const edit = new vscode.WorkspaceEdit();
      const notebook = cell.notebook;
      const index = cell.index;

      // Get existing metadata
      const existingMetadata = cell.metadata || {};
      const taxiMetadata = existingMetadata.taxi || {};

      // Update taxi metadata
      const newTaxiMetadata = stubsId
         ? { ...taxiMetadata, stubsId }
         : { ...taxiMetadata };

      // Remove stubsId if undefined
      if (!stubsId && newTaxiMetadata.stubsId) {
         delete newTaxiMetadata.stubsId;
      }

      // Create new metadata object
      const newMetadata = {
         ...existingMetadata,
         taxi: newTaxiMetadata,
      };

      // Apply the edit
      const notebookEdit = vscode.NotebookEdit.updateCellMetadata(
         index,
         newMetadata
      );

      edit.set(notebook.uri, [notebookEdit]);
      await vscode.workspace.applyEdit(edit);
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
