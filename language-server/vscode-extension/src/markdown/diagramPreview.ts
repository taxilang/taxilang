/**
 * Markdown Preview Script for Taxi Diagrams
 *
 * This script runs in the markdown preview webview and:
 * 1. Finds taxi-diagram elements
 * 2. Calls VS Code command to fetch diagram data
 * 3. Renders the query plan visualization
 */

interface DiagramDataRequest {
   names: string[];
   projectRoot: string;
}

interface QueryPlanResponse {
   diagramData: any;
}

// Declare VS Code API that's available in markdown preview
declare const vscode: {
   postMessage(message: any): void;
};

/**
 * Initialize all taxi-diagram elements on page load
 */
function initializeDiagrams() {
   const diagramElements = document.querySelectorAll('[data-taxi-diagram="true"]');

   diagramElements.forEach((element, index) => {
      const htmlElement = element as HTMLElement;
      const namesJson = htmlElement.getAttribute('data-names');

      if (!namesJson) {
         showError(htmlElement.id, 'No names specified for diagram');
         return;
      }

      try {
         const names: string[] = JSON.parse(namesJson);

         // Assign unique ID for this container
         const containerId = `taxi-diagram-${index}`;
         htmlElement.id = containerId;

         // Request diagram data via VS Code command
         fetchDiagramData(containerId, names);
      } catch (e) {
         showError(htmlElement.id, `Failed to parse diagram names: ${e}`);
      }
   });
}

/**
 * Render the query plan diagram in the container
 */
function renderDiagram(containerId: string, diagramData: any) {
   const container = document.getElementById(containerId);
   if (!container) {
      console.error(`Container ${containerId} not found`);
      return;
   }

   // Hide loading indicator
   const loadingEl = container.querySelector('.taxi-diagram-loading') as HTMLElement;
   if (loadingEl) {
      loadingEl.style.display = 'none';
   }

   // Show content area
   const contentEl = container.querySelector('.taxi-diagram-content') as HTMLElement;
   if (!contentEl) {
      console.error('Content element not found');
      return;
   }

   contentEl.style.display = 'block';

   // For now, just render the diagram data as JSON
   // TODO: Use the actual query plan visualization component
   const pre = document.createElement('pre');
   pre.style.cssText = 'margin: 0; padding: 16px; background: white; border-radius: 4px; overflow: auto;';
   pre.textContent = JSON.stringify(diagramData, null, 2);

   contentEl.appendChild(pre);
}

/**
 * Fetch diagram data by executing VS Code command
 */
async function fetchDiagramData(containerId: string, names: string[]) {
   try {
      // For now, just show placeholder
      // TODO: Properly wire up command execution from markdown preview
      // The command 'taxi.getDiagramData' is registered and ready to use
      showPlaceholder(containerId, names);
   } catch (error) {
      showError(containerId, `Failed to fetch diagram data: ${error}`);
   }
}

/**
 * Show placeholder with diagram names (when LSP integration not available)
 */
function showPlaceholder(containerId: string, names: string[]) {
   const container = document.getElementById(containerId);
   if (!container) {
      console.error(`Container ${containerId} not found`);
      return;
   }

   const loadingEl = container.querySelector('.taxi-diagram-loading') as HTMLElement;
   if (loadingEl) {
      loadingEl.innerHTML = `
         <div>
            <div style="font-weight: bold; margin-bottom: 8px;">📊 Taxi Diagram</div>
            <div style="font-size: 0.9em; color: #666;">
               <div>Types/Services:</div>
               <ul style="margin: 8px 0; padding-left: 20px;">
                  ${names.map(name => `<li>${escapeHtml(name)}</li>`).join('')}
               </ul>
               <div style="font-style: italic; margin-top: 8px;">
                  Diagram rendering will be available when implementation is complete
               </div>
            </div>
         </div>
      `;
   }
}

/**
 * Show error message in the diagram container
 */
function showError(containerId: string, error: string) {
   const container = document.getElementById(containerId);
   if (!container) {
      console.error(`Container ${containerId} not found for error: ${error}`);
      return;
   }

   const loadingEl = container.querySelector('.taxi-diagram-loading') as HTMLElement;
   if (loadingEl) {
      loadingEl.innerHTML = `
         <div style="color: #d32f2f;">
            <div style="font-weight: bold; margin-bottom: 8px;">⚠️ Error loading diagram</div>
            <div style="font-size: 0.9em;">${error}</div>
         </div>
      `;
   }
}

/**
 * Escape HTML for safe rendering
 */
function escapeHtml(text: string): string {
   const div = document.createElement('div');
   div.textContent = text;
   return div.innerHTML;
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
   document.addEventListener('DOMContentLoaded', initializeDiagrams);
} else {
   initializeDiagrams();
}

// Re-initialize when markdown content changes (for live preview)
const observer = new MutationObserver(() => {
   initializeDiagrams();
});

observer.observe(document.body, {
   childList: true,
   subtree: true
});
