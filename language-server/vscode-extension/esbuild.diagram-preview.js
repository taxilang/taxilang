const esbuild = require('esbuild');

esbuild.build({
  entryPoints: ['src/markdown/diagramPreview.ts'],
  bundle: true,
  outfile: 'out/markdown/diagramPreview.js',
  format: 'iife',
  platform: 'browser',
  target: 'es2020',
  sourcemap: true,
}).catch(() => process.exit(1));
