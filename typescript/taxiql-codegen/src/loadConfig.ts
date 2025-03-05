import {CodegenConfig} from '@orbitalhq/taxiql-client';
import {transform} from 'esbuild';
import {readFile, writeFile} from 'fs/promises';
import minimist from 'minimist';
import {resolve} from 'path';

const DEFAULT_CONFIG_PATH = './codegen.config.ts'
const DEFAULT_CONFIG: CodegenConfig = {
   "documents": [
      "./src/**/*.{js,jsx,ts,tsx}",
   ],
   "taxiConf": "./taxi/taxi.conf",
   "outputDir": "./src/generated",
   "taxiSourcePath": "src/**/*.taxi",
   "watch": true,
   "orbitalServerUrl": "http://localhost:9022/"
}

const DEFAULT_CONFIG_STRING = `
import {CodegenConfig} from '@orbitalhq/taxiql-client';

const config: CodegenConfig = ${JSON.stringify(DEFAULT_CONFIG, null, 2)}

export default config;
`

export default async function loadConfig(): Promise<CodegenConfig> {
   const {config} = minimist(process.argv.slice(2)) ?? {};
   const configPath = resolve(process.cwd(), config ?? DEFAULT_CONFIG_PATH);

   const tsCode = await readOrCreateConfigFile(configPath)
   return compileConfig(tsCode, configPath)
}


async function readOrCreateConfigFile(configPath: string): Promise<string> {
   try {
      // Read the TypeScript config file
      return await readFile(configPath, 'utf-8');
   } catch (error: any) {
      if (error.message.startsWith('ENOENT: no such file or directory')) {
         console.warn(`No taxi-codegen.config.ts file found at ${configPath} - generating a default one. You can specify a custom config file by passing --config`)
         await writeFile(configPath, DEFAULT_CONFIG_STRING);
         return DEFAULT_CONFIG_STRING;
      } else {
         throw new Error(`Failed to load config from ${configPath}: ${error.message}`);
      }
   }
}

async function compileConfig(tsCode: string, configPath: string): Promise<CodegenConfig> {
   // Transpile to JavaScript using esbuild
   const {code} = await transform(tsCode, {loader: 'ts', format: 'cjs'});

   // Execute the transpiled JS
   const module: { exports: any } = {exports: {}};
   const func = new Function('module', 'exports', code);
   func(module, module.exports);

   const config = (module.exports.default ?? module.exports) as CodegenConfig;

   if (!config || typeof config !== 'object') {
      throw new Error(`Config file does not export a valid object: ${configPath}`);
   }

   return config;
}
