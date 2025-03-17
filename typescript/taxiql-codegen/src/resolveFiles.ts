import {CodegenConfig} from '../../taxiql-client/src/types'; // I tried REALLY HARD to get inter-module links working. Nx and everything. But couldn't, and screw you, TS. See giving-up-on-modules.md
import fg from 'fast-glob';

export default async function resolveFiles(documents: string[], config: CodegenConfig) {
  const resolvedFiles = [];
  for (const pattern of documents) {
    // Normalize Windows paths to POSIX-style
    const posixPattern = pattern.replace(/\\/g, '/');
    const files = await fg(posixPattern, {
      ignore: [
        config.outputDir + '/**', // Exclude generated files
        '!./src/**/*.d.ts', // Exclude type defs
      ],
    });
    resolvedFiles.push(...files);
  }
  return resolvedFiles
}
