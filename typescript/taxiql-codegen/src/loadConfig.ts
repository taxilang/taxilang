import {CodegenConfig} from '@orbitalhq/taxiql-client';
import {transform} from 'esbuild';
import {readFile} from 'fs/promises';
import minimist from 'minimist';
import {resolve} from 'path';

const DEFAULT_CONFIG = './codegen.config.ts'

export default async function loadConfig(): Promise<CodegenConfig> {
  const { config } = minimist(process.argv.slice(2)) ?? {};
  const configPath = resolve(process.cwd(), config ?? DEFAULT_CONFIG);

  try {
    // Read the TypeScript config file
    const tsCode = await readFile(configPath, 'utf-8');

    // Transpile to JavaScript using esbuild
    const { code } = await transform(tsCode, { loader: 'ts', format: 'cjs' });

    // Execute the transpiled JS
    const module: { exports: any } = { exports: {} };
    const func = new Function('module', 'exports', code);
    func(module, module.exports);

    const config = (module.exports.default ?? module.exports) as CodegenConfig;

    if (!config || typeof config !== 'object') {
      throw new Error(`Config file does not export a valid object: ${configPath}`);
    }

    return config;
  } catch (error: any) {
    throw new Error(`Failed to load config from ${configPath}: ${error.message}`);
  }
}
