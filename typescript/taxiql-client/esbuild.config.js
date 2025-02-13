import esbuild from 'esbuild';

const config = {
   entryPoints: ['./src/index.ts'],
   outfile: './dist/index.js',
   bundle: true,
   platform: 'browser',
   external: ['react'],
   format: 'esm',
   sourcemap: true,
};

esbuild.build(config).catch(() => process.exit(1));
