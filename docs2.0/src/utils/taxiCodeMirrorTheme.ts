import { EditorView } from '@codemirror/view';
import { Extension } from '@codemirror/state';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags } from '@lezer/highlight';

// Create a custom theme that matches the Prism colors
export const taxiDarkTheme = (): Extension => {
  
  // Define syntax highlighting that matches Prism colors
  const prismColorScheme = HighlightStyle.define([
    // Keywords: text-sky-300 
    { tag: tags.keyword, color: '#7dd3fc' },
    
    // Functions/Class names: text-pink-400
    { tag: tags.function(tags.variableName), color: '#f472b6' },
    { tag: tags.className, color: '#f472b6' },
    { tag: tags.typeName, color: '#f472b6' },
    
    // Our custom "type" token (for built-ins and class names): text-pink-400
    { tag: tags.standard(tags.name), color: '#f472b6' },
    
    // Strings: text-sky-300
    { tag: tags.string, color: '#7dd3fc' },
    
    // Comments: text-slate-400
    { tag: tags.comment, color: '#94a3b8' },
    
    // Punctuation: text-slate-500
    { tag: tags.punctuation, color: '#64748b' },
    { tag: tags.bracket, color: '#64748b' },
    { tag: tags.paren, color: '#64748b' },
    { tag: tags.separator, color: '#64748b' },
    
    // Numbers: default light color
    { tag: tags.number, color: '#e2e8f0' },
    
    // Variables: default light color
    { tag: tags.variableName, color: '#e2e8f0' },
    
    // Operators: text-slate-400 (same as comments)
    { tag: tags.operator, color: '#94a3b8' },
    
    // Fallback for any "type" tokens from our custom language
    { tag: tags.name, color: '#f472b6' },
  ]);
  
  // Add our custom background styles
  const customBackground = EditorView.theme({
    '&': {
      backgroundColor: 'rgba(30, 41, 59, 0.6) !important',
    },
    '.cm-gutters': {
      backgroundColor: 'rgba(30, 41, 59, 0.8) !important',
      borderRight: '1px solid rgba(30, 41, 59, 1)',
    },
    '.cm-activeLineGutter': {
      backgroundColor: 'rgba(30, 41, 59, 1) !important',
    },
    '.cm-editor': {
      color: '#e2e8f0', // Default text color
    },
    '.cm-content': {
      color: '#e2e8f0', // Default text color
    },
    '.cm-cursor, .cm-dropCursor': {
      borderLeftColor: '#ffffff !important', // White cursor
    }
  });
  
  // Combine the syntax highlighting with our background customizations
  return [syntaxHighlighting(prismColorScheme), customBackground];
};