/**
 * Markdown-it plugin for rendering taxi-diagram code blocks
 *
 * Handles code blocks marked as ```taxi-diagram
 * For now, renders a placeholder - actual diagram rendering to be implemented
 */

export function taxiDiagramPlugin(md: any): void {
   // Store the default fence renderer
   const defaultFence = md.renderer.rules.fence?.bind(md.renderer.rules) ||
      ((tokens: any[], idx: number, options: any, env: any, self: any) => {
         return self.renderToken(tokens, idx, options);
      });

   // Override fence renderer to handle taxi-diagram blocks
   md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
      const token = tokens[idx];
      const info = token.info.trim();

      // Check if this is a taxi-diagram block
      if (info === 'taxi-diagram') {
         const content = token.content.trim();
         const lines = content.split('\n').map((line: string) => line.trim()).filter((line: string) => line);

         // Render a div with data attributes that will be picked up by the markdown preview script
         // The script will fetch diagram data and render it
         return `<div class="taxi-diagram-container"
                     data-taxi-diagram="true"
                     data-names="${escapeHtml(JSON.stringify(lines))}"
                     style="
                        border: 1px solid #ddd;
                        border-radius: 4px;
                        padding: 16px;
                        margin: 16px 0;
                        background: #fafafa;
                        min-height: 400px;
                     ">
            <div class="taxi-diagram-loading" style="
               display: flex;
               align-items: center;
               justify-content: center;
               height: 400px;
               color: #666;
               font-family: var(--vscode-font-family);
            ">
               <div>
                  <div style="margin-bottom: 8px; text-align: center;">⏳ Loading diagram...</div>
                  <div style="font-size: 0.9em; color: #999; text-align: center;">
                     (${lines.length} ${lines.length === 1 ? 'item' : 'items'})
                  </div>
               </div>
            </div>
            <div class="taxi-diagram-content" style="display: none;"></div>
         </div>`;
      }

      // Fall back to default fence rendering for other code blocks
      return defaultFence(tokens, idx, options, env, self);
   };
}

/**
 * Escape HTML special characters
 */
function escapeHtml(text: string): string {
   const map: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
   };
   return text.replace(/[&<>"']/g, (m) => map[m]);
}
