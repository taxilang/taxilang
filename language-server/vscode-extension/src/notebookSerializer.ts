import * as vscode from "vscode";

/**
 * Serializer for TaxiQL Notebook files (.taxiqnb)
 * Handles reading and writing notebook data in a JSON format
 */
export class TaxiQLNotebookSerializer implements vscode.NotebookSerializer {
   async deserializeNotebook(
      content: Uint8Array,
      _token: vscode.CancellationToken
   ): Promise<vscode.NotebookData> {
      const contents = new TextDecoder().decode(content);

      let raw: RawNotebookData;
      try {
         raw = contents ? JSON.parse(contents) : { cells: [] };
      } catch {
         raw = { cells: [] };
      }

      const cells = raw.cells.map((item) =>
         new vscode.NotebookCellData(
            item.kind === "markdown"
               ? vscode.NotebookCellKind.Markup
               : vscode.NotebookCellKind.Code,
            item.value,
            item.language
         )
      );

      // Restore cell metadata
      cells.forEach((cell, index) => {
         if (raw.cells[index].metadata) {
            cell.metadata = raw.cells[index].metadata;
         }
      });

      const notebookData = new vscode.NotebookData(cells);

      // Restore notebook metadata
      if (raw.metadata) {
         notebookData.metadata = raw.metadata;
      }

      return notebookData;
   }

   async serializeNotebook(
      data: vscode.NotebookData,
      _token: vscode.CancellationToken
   ): Promise<Uint8Array> {
      const contents: RawNotebookData = {
         cells: [],
      };

      for (const cell of data.cells) {
         contents.cells.push({
            kind: cell.kind === vscode.NotebookCellKind.Markup ? "markdown" : "code",
            language: cell.languageId,
            value: cell.value,
            metadata: cell.metadata,
         });
      }

      // Save notebook metadata
      if (data.metadata) {
         contents.metadata = data.metadata;
      }

      return new TextEncoder().encode(JSON.stringify(contents, null, 2));
   }
}

interface RawNotebookCell {
   kind: "markdown" | "code";
   language: string;
   value: string;
   metadata?: { [key: string]: any };
}

interface RawNotebookData {
   cells: RawNotebookCell[];
   metadata?: { [key: string]: any };
}
