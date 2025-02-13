import {CodegenConfig} from '@orbitalhq/taxiql-client';
import runBinary from './binaryUtil';
import taxiExtractor from './taxiExtractor';

export default async function processFiles(resolvedFiles: string[], config: CodegenConfig) {
  const { outputDir, taxiConf, taxiSourcePath } = config;
  try {
    console.log('Resolved resolvedFiles:', resolvedFiles);

    // Process files with the extractor
    const transformedResults = await taxiExtractor(resolvedFiles);

    try {
      const workingDir = process.cwd();
      const sourceArgs = transformedResults.flatMap(result => ['--source', result]); // NOTE: this needs `"${result}"` to run properly from the command line
      const config = ['--config', `${taxiConf}`]
      const output = ['--output', `${outputDir}`]
      const pathArgs = ['--path', `${taxiSourcePath}`]
      const args = [...config, ...output, ...pathArgs, ...sourceArgs]
      // Spawn the binary with arguments
      const child = runBinary(args)
      child?.on('error', (err) => {
        console.error(`Failed to start process: ${err.message}`);
      });

      child?.on('close', (code) => {
        if (code !== 0) {
          console.error(`Process exited with code: ${code}`);
        } else {
          console.log('Process completed successfully.');
        }
      });
    } catch (error) {
      console.log(error)
    }
  } catch (error) {
    console.error('Error processing files:', error);
  }
}
