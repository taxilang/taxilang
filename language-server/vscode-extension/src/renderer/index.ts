/**
 * TaxiQL Notebook Renderer
 *
 * This renderer handles custom MIME types for TaxiQL notebook outputs:
 * - application/vnd.taxi.plan+json: Query plan visualization
 * - application/vnd.taxi.results+json: Execution results
 *
 * For now, this is a placeholder that validates HTML rendering works.
 * Rich UI components (React Flow, etc.) will be added later.
 */

// Renderer activation function
// This runs in the webview context, not the extension context
type RendererContext = any;

interface OutputItem {
   json(): any;
   mime: string;
}

export const activate = (context: RendererContext) => {
   return {
      renderOutputItem(outputItem: OutputItem, element: HTMLElement) {
         const data = outputItem.json();
         const mimeType = outputItem.mime;

         // Create container
         const container = document.createElement("div");
         container.style.padding = "10px";
         container.style.backgroundColor = "#f5f5f5";
         container.style.border = "1px solid #ddd";
         container.style.borderRadius = "4px";
         container.style.fontFamily = "monospace";
         container.style.fontSize = "12px";

         if (mimeType === "application/vnd.taxi.plan+json") {
            renderPlan(data, container);
         } else if (mimeType === "application/vnd.taxi.results+json") {
            renderResults(data, container);
         } else {
            container.innerHTML = `<div style="color: red;">Unknown MIME type: ${mimeType}</div>`;
         }

         element.appendChild(container);
      },
   };
};

function renderPlan(data: any, container: HTMLElement) {
   const header = document.createElement("div");
   header.style.fontWeight = "bold";
   header.style.marginBottom = "10px";
   header.style.color = "#0066cc";
   header.innerHTML = "📊 Query Plan Visualization (Placeholder)";
   container.appendChild(header);

   const info = document.createElement("div");
   info.style.marginBottom = "10px";
   info.style.color = "#666";
   info.innerHTML = "This is a placeholder. Rich React Flow visualization will be added later.";
   container.appendChild(info);

   const dataSection = document.createElement("div");
   dataSection.style.backgroundColor = "#fff";
   dataSection.style.padding = "10px";
   dataSection.style.borderRadius = "4px";
   dataSection.style.maxHeight = "400px";
   dataSection.style.overflow = "auto";

   // Display plan data
   const pre = document.createElement("pre");
   pre.style.margin = "0";
   pre.style.whiteSpace = "pre-wrap";
   pre.textContent = JSON.stringify(data, null, 2);
   dataSection.appendChild(pre);

   container.appendChild(dataSection);

   // Show metadata if available
   if (data.metadata) {
      const metaSection = document.createElement("div");
      metaSection.style.marginTop = "10px";
      metaSection.style.padding = "10px";
      metaSection.style.backgroundColor = "#ffffcc";
      metaSection.style.borderRadius = "4px";
      metaSection.innerHTML = `<strong>Metadata:</strong> ${JSON.stringify(data.metadata)}`;
      container.appendChild(metaSection);
   }
}

function renderResults(data: any, container: HTMLElement) {
   const header = document.createElement("div");
   header.style.fontWeight = "bold";
   header.style.marginBottom = "10px";
   header.style.color = "#00aa00";
   header.innerHTML = "✅ Execution Results (Placeholder)";
   container.appendChild(header);

   const info = document.createElement("div");
   info.style.marginBottom = "10px";
   info.style.color = "#666";
   info.innerHTML = "This is a placeholder. Rich table/JSON visualization will be added later.";
   container.appendChild(info);

   // Show execution metadata
   if (data.metadata) {
      const metaSection = document.createElement("div");
      metaSection.style.marginBottom = "10px";
      metaSection.style.padding = "10px";
      metaSection.style.backgroundColor = "#e6f7ff";
      metaSection.style.borderRadius = "4px";

      const metaItems = [
         `Rows: ${data.metadata.rowCount || "unknown"}`,
         `Execution Time: ${data.metadata.executionTime || "unknown"}`,
         `Status: ${data.metadata.status || "unknown"}`,
      ];

      if (data.metadata.stubsId) {
         metaItems.push(`Stubs ID: ${data.metadata.stubsId}`);
      }

      metaSection.innerHTML = `<strong>📈 Execution Summary:</strong><br/>${metaItems.join(" | ")}`;
      container.appendChild(metaSection);
   }

   // Display results data
   const dataSection = document.createElement("div");
   dataSection.style.backgroundColor = "#fff";
   dataSection.style.padding = "10px";
   dataSection.style.borderRadius = "4px";
   dataSection.style.maxHeight = "400px";
   dataSection.style.overflow = "auto";

   const pre = document.createElement("pre");
   pre.style.margin = "0";
   pre.style.whiteSpace = "pre-wrap";
   pre.textContent = JSON.stringify(data, null, 2);
   dataSection.appendChild(pre);

   container.appendChild(dataSection);
}
