const esbuild = require('esbuild');
const path = require('path');

esbuild.build({
   entryPoints: ['src/renderer/index.tsx'],
   bundle: true,
   outfile: 'out/renderer/index.js',
   format: 'esm',
   platform: 'browser',
   target: 'es2020',
   sourcemap: true,
   minify: false, // Disable minification for debugging
   jsx: 'automatic',
   loader: {
      '.css': 'text', // Load CSS as text so we can inject it
      '.svg': 'dataurl',
      '.png': 'dataurl',
      '.jpg': 'dataurl',
      '.ttf': 'dataurl', // Codicon font files
      '.woff': 'dataurl',
      '.woff2': 'dataurl',
   },
   external: [],
   alias: {
      // Allow imports from the docs2.0 components directory
      '@query-plan': path.resolve(__dirname, '../../docs2.0/src/components/query-plan'),
      // Force all React imports to use the same version (from VSCode extension's node_modules)
      'react': path.resolve(__dirname, 'node_modules/react'),
      'react-dom': path.resolve(__dirname, 'node_modules/react-dom'),
      'react/jsx-runtime': path.resolve(__dirname, 'node_modules/react/jsx-runtime'),
   },
}).catch(() => process.exit(1));
