import {CodegenConfig} from '@orbitalhq/taxiql-client';

const config: CodegenConfig = {
  "documents": [
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  "taxiConf": "../taxi/taxi.conf",
  "outputDir": "./src/generated",
  "taxiSourcePath": "src/**/*.taxi",
  "watch": true,
  "orbitalServerUrl": "http://localhost:9022/"
}

export default config;
