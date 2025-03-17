import esbuild from 'esbuild';

const config = {
   entryPoints: ['./src/codeGen.ts'],
   outfile: './dist/build.js',
   bundle: true,
   platform: 'node',
   // Don't bundle node_modules
   packages: 'external',
   external: ['esbuild'],
   sourcemap: true,
   format: 'esm'
};

esbuild.build(config).catch(() => process.exit(1));
