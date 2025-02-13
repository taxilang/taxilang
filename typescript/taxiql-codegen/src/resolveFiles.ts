import {CodegenConfig} from '@orbitalhq/taxiql-client';
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
