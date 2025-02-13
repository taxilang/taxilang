import {CodegenConfig} from '@orbitalhq/taxiql-client';

const config: CodegenConfig = {
  "documents": [
    "./**/*.{js,jsx,ts,tsx}",
  ],
  "taxiConf": "../taxi/taxi.conf",
  "outputDir": "./src/generated",
  "taxiSourcePath": "src/**/*.taxi",
  "watch": true,
  "orbitalServerUrl": "http://localhost:9022/"
}

export default config;
