import { chdir } from 'process';

// Change working directory before tests run
chdir('./tests');

console.log('Current working directory:', process.cwd());
