/**
 * NOTE:
 * This NPM postInstall approach for getting the correct binary to do the
 * codegen with has been abandoned in favour of just relying on optionalDependencies
 * being enabled when the user npm installs this package.
 */


import fs from 'fs';
import https from 'https';
import path from 'path';
import process from 'process';
import zlib from 'zlib';
import {BINARY_NAME, PLATFORM_SPECIFIC_PACKAGE_NAME} from '../src/binaryUtil';

// Adjust the version you want to install. You can also make this dynamic.
// TODO: instrospect package.json version here
const BINARY_DISTRIBUTION_VERSION = '1.0.0';

if (!PLATFORM_SPECIFIC_PACKAGE_NAME) {
  console.error('Unsupported platform!');
  process.exit(1);
}

// Compute the path we want to emit the fallback binary to
const fallbackBinaryPath: string = path.join(__dirname, BINARY_NAME);

/**
 * Makes an HTTPS request and returns the response as a buffer.
 */
function makeRequest(url: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    https
      .get(url, (response) => {
        if (response.statusCode && response.statusCode >= 200 && response.statusCode < 300) {
          const chunks: Buffer[] = [];
          response.on('data', (chunk: Buffer) => chunks.push(chunk));
          response.on('end', () => resolve(Buffer.concat(chunks)));
        } else if (response.statusCode && response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
          // Follow redirects
          makeRequest(response.headers.location).then(resolve).catch(reject);
        } else {
          reject(new Error(`npm responded with status code ${response.statusCode} when downloading the package from ${url}!`));
        }
      })
      .on('error', reject);
  });
}

/**
 * Extracts a specific file from a tarball buffer.
 */
function extractFileFromTarball(tarballBuffer: Buffer, filepath: string): Buffer | undefined {
  let offset = 0;
  while (offset < tarballBuffer.length) {
    const header = tarballBuffer.subarray(offset, offset + 512);
    offset += 512;

    const fileName = header.toString('utf-8', 0, 100).replace(/\0.*/g, '');
    const fileSize = parseInt(header.toString('utf-8', 124, 136).replace(/\0.*/g, ''), 8);

    if (fileName === filepath) {
      return tarballBuffer.subarray(offset, offset + fileSize);
    }

    // Clamp offset to the upper multiple of 512
    offset = (offset + fileSize + 511) & ~511;
  }

  return undefined;
}

/**
 * Downloads the binary tarball from npm and extracts the necessary file.
 */
async function downloadBinaryFromNpm(): Promise<void> {
  try {
    // Download the tarball of the right binary distribution package
    const tarballDownloadBuffer: Buffer = await makeRequest(
      // TODO: is this path this correct?
      `https://registry.npmjs.org/${PLATFORM_SPECIFIC_PACKAGE_NAME}/-/${PLATFORM_SPECIFIC_PACKAGE_NAME}-${BINARY_DISTRIBUTION_VERSION}.tgz`
    );

    const tarballBuffer: Buffer = zlib.unzipSync(tarballDownloadBuffer);

    // Extract binary from package and write to disk
    const extractedBinary: Buffer | undefined = extractFileFromTarball(tarballBuffer, `package/bin/${BINARY_NAME}`);

    if (!extractedBinary) {
      throw new Error(`Could not extract binary '${BINARY_NAME}' from tarball.`);
    }

    fs.writeFileSync(fallbackBinaryPath, extractedBinary);

    // Make binary executable
    fs.chmodSync(fallbackBinaryPath, 0o755);
  } catch (error) {
    console.error('Error downloading binary:', error);
    process.exit(1);
  }
}

/**
 * Checks if the platform-specific package is installed.
 */
function isPlatformSpecificPackageInstalled(): boolean {
  try {
    // Resolving will fail if the optionalDependency was not installed
    require.resolve(`${PLATFORM_SPECIFIC_PACKAGE_NAME}/bin/${BINARY_NAME}`);
    return true;
  } catch {
    return false;
  }
}

// Skip downloading the binary if it was already installed via optionalDependencies
if (!isPlatformSpecificPackageInstalled()) {
  console.log('Platform-specific package not found. Will manually download binary.');
  downloadBinaryFromNpm();
} else {
  console.log('Platform-specific package already installed.');
}
