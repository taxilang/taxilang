import {spawn} from 'child_process';
import {ChildProcess} from 'node:child_process';
import path from 'path';
import process from 'process';

// Lookup table for all platforms and binary distribution packages (well, the ones we support)
const binaryDistributionPackages: Partial<Record<NodeJS.Platform, string>> = {
  darwin: 'taxiql-codegen-cli-darwin',
  linux: 'taxiql-codegen-cli-linux',
  freebsd: 'taxiql-codegen-cli-linux',
  win32: 'taxiql-codegen-cli-win32',
};

// Windows binaries end with .exe so we need to special case them.
export const BINARY_NAME = process.platform === 'win32' ? 'taxiql-codegen-cli.exe' : 'taxiql-codegen-cli';

// Determine package name for this platform
export const PLATFORM_SPECIFIC_PACKAGE_NAME = binaryDistributionPackages[process.platform];

if (!PLATFORM_SPECIFIC_PACKAGE_NAME) {
  console.error('Unsupported platform!');
  process.exit(1);
}

function getBinaryPath() {
  try {
    // Resolving will fail if the optionalDependency was not installed
    // Resolve from the nearest node_modules root
    return require.resolve(`@orbitalhq/${PLATFORM_SPECIFIC_PACKAGE_NAME}/bin/${BINARY_NAME}`, {
      paths: [path.resolve(__dirname, 'node_modules')],
    });
  } catch (e) {
    // NOTE: if developing this locally, you'll probably end up getting your binary from here
    try {
      return require.resolve(`../packages/${process.platform === 'freebsd' ? 'linux' : process.platform}/bin/${BINARY_NAME}`);
    } catch (e) {
      console.warn('Ensure you allow optional dependencies to be installed to download the correct TaxiQL compiler binary for your environment')
    }
  }
}

const runBinary = (args: any[]): ChildProcess | null => {
  const binaryPath = getBinaryPath();

  if (!binaryPath) {
    console.error('Error: Binary path is undefined. Cannot execute the binary.');
    return null;
  }

  try {
    console.log('Command:', binaryPath, args.join(' '));
    return spawn(binaryPath, args, {
      stdio: 'inherit',
    });
  } catch (error: any) {
    console.error(`Error running the binary: ${error.message}`);
    return null;
  }
};

export default runBinary
