/**
 * CodeLens Provider for Taxi Diagrams in Markdown
 *
 * Scans markdown files for taxi-diagram code blocks and provides
 * a "Preview Diagram" CodeLens above each block.
 */

import * as vscode from 'vscode';

export class TaxiDiagramCodeLensProvider implements vscode.CodeLensProvider {
   private _onDidChangeCodeLenses: vscode.EventEmitter<void> = new vscode.EventEmitter<void>();
   public readonly onDidChangeCodeLenses: vscode.Event<void> = this._onDidChangeCodeLenses.event;

   /**
    * Provide CodeLenses for taxi-diagram blocks
    */
   provideCodeLenses(
      document: vscode.TextDocument,
      token: vscode.CancellationToken
   ): vscode.CodeLens[] | Thenable<vscode.CodeLens[]> {
      const codeLenses: vscode.CodeLens[] = [];
      const text = document.getText();

      // Regex to find taxi-diagram code blocks
      // Matches: ```taxi-diagram ... ```
      const regex = /^```taxi-diagram\s*$/gm;
      let match;

      while ((match = regex.exec(text)) !== null) {
         const startPos = document.positionAt(match.index);
         const range = new vscode.Range(startPos, startPos);

         // Create CodeLens with command to preview the diagram
         const codeLens = new vscode.CodeLens(range, {
            title: "$(graph) Preview Diagram",
            command: "taxi.showDiagramPreview",
            arguments: [document.uri, startPos.line]
         });

         codeLenses.push(codeLens);
      }

      return codeLenses;
   }

   /**
    * Refresh CodeLenses when document changes
    */
   refresh(): void {
      this._onDidChangeCodeLenses.fire();
   }
}
