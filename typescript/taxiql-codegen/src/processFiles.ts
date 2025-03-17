import {CodegenConfig} from '../../taxiql-client/src/types'; // I tried REALLY HARD to get inter-module links working. Nx and everything. But couldn't, and screw you, TS. See giving-up-on-modules.md
import runBinary from './binaryUtil.js';
import taxiExtractor from './taxiExtractor.js';
import fs from 'fs';
import path from 'path';

export default async function processFiles(resolvedFiles: string[], config: CodegenConfig) {
  const { outputDir, taxiConf, taxiSourcePath } = config;
  try {
    console.log('Resolved resolvedFiles:', resolvedFiles);

    // Verify that taxi.conf exists at the specified location
    const taxiConfPath = path.resolve(process.cwd(), taxiConf);
    if (!fs.existsSync(taxiConfPath)) {
      const errorMessage = `No taxi.conf not found at ${taxiConfPath}`;
      throw new Error(errorMessage);
    }

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
      const child = runBinary(args);

      // Variables to capture stdout and stderr
      let stdout = '';
      let stderr = '';

      // Capture stdout
      child?.stdout?.on('data', (data) => {
        const output = data.toString();
        stdout += output;
        // Still display output in real-time
        process.stdout.write(output);
      });

      // Capture stderr
      child?.stderr?.on('data', (data) => {
        const output = data.toString();
        stderr += output;
        // Still display output in real-time
        process.stderr.write(output);
      });

      child?.on('error', (err) => {
        console.error(`Failed to start process: ${err.message}`);
      });

      child?.on('close', (code) => {
        if (code !== 0) {
          console.error(`Process exited with code: ${code}`);
          console.error('Process stderr:');
          console.error(stderr || '(No stderr output)');

          if (!stderr) {
            console.error('Process stdout (for debugging):');
            console.error(stdout || '(No stdout output)');
          }

          // Throw an error to stop the process
          throw new Error(`TaxiQL codegen failed with exit code ${code}`);
        } else {
          console.log('Process completed successfully.');
        }
      });
    } catch (error) {
      console.error('Error running codegen tool:', error);
      // Re-throw to propagate the error
      throw error;
    }
  } catch (error) {
    console.error('Error processing files:');
    if (error instanceof Error) {
      console.error(`${error.name}: ${error.message}`);
      if (error.stack) {
        console.error('Stack trace:');
        console.error(error.stack);
      }
    } else {
      console.error(error);
    }
    // Re-throw to ensure the process exits with a non-zero code
    throw error;
  }
}
