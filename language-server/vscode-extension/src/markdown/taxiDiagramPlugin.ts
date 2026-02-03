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

         // Render placeholder (actual diagram rendering to be implemented)
         return `<div class="taxi-diagram-container" style="
            border: 2px dashed #888;
            border-radius: 4px;
            padding: 16px;
            margin: 16px 0;
            background: #f5f5f5;
            font-family: monospace;
         ">
            <div style="font-weight: bold; margin-bottom: 8px; color: #666;">
               🎨 Taxi Diagram (${lines.length} ${lines.length === 1 ? 'item' : 'items'})
            </div>
            <div style="background: white; padding: 8px; border-radius: 2px;">
               <pre style="margin: 0; color: #333;">${escapeHtml(content)}</pre>
            </div>
            <div style="margin-top: 8px; font-size: 0.9em; color: #999;">
               Diagram rendering will be implemented
            </div>
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
