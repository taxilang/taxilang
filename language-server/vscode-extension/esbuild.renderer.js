const esbuild = require('esbuild');

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
}).catch(() => process.exit(1));
