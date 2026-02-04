const esbuild = require('esbuild');
const path = require('path');

esbuild.build({
   entryPoints: ['src/webview/stub-editor/index.tsx'],
   bundle: true,
   outfile: 'out/webview/stub-editor.js',
   format: 'iife',
   platform: 'browser',
   target: 'es2020',
   sourcemap: true,
   minify: false,
   jsx: 'automatic',
   loader: {
      '.css': 'text',
      '.svg': 'dataurl',
      '.png': 'dataurl',
      '.jpg': 'dataurl',
   },
   external: [],
   alias: {
      'react': path.resolve(__dirname, 'node_modules/react'),
      'react-dom': path.resolve(__dirname, 'node_modules/react-dom'),
      'react/jsx-runtime': path.resolve(__dirname, 'node_modules/react/jsx-runtime'),
   },
}).catch(() => process.exit(1));
