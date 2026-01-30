const esbuild = require('esbuild');

esbuild.build({
   entryPoints: ['src/renderer/index.ts'],
   bundle: true,
   outfile: 'out/renderer/index.js',
   format: 'esm',
   platform: 'browser',
   target: 'es2020',
   sourcemap: true,
   minify: true,
}).catch(() => process.exit(1));
