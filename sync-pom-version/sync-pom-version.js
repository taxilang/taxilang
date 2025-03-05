#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { XMLParser } = require('fast-xml-parser');
const { Command } = require('commander');

/**
 * Updates package.json version with the version from pom.xml
 * Can replace -SNAPSHOT with a semver beta version if buildNumber is provided
 *
 * Usage:
 *   node update-version.js
 *   node update-version.js --build 123
 */

// Parse command line arguments
const program = new Command();
program
   .option('-b, --build <number>', 'build number to replace -SNAPSHOT with a beta version')
   .parse(process.argv);

const options = program.opts();
const buildNumber = options.build;

// Read POM file
try {
   // Read pom.xml content
   const pomPath = path.resolve(process.cwd(), 'pom.xml');
   const pomContent = fs.readFileSync(pomPath, 'utf8');

   // Parse XML to extract version
   const parser = new XMLParser();
   const pomData = parser.parse(pomContent);

   let version;

   // Check if version is directly in the project
   if (pomData.project && pomData.project.version) {
      version = pomData.project.version;
      console.log(`Found version in pom.xml: ${version}`);
   }
   // Check if version is in the parent
   else if (pomData.project && pomData.project.parent && pomData.project.parent.version) {
      version = pomData.project.parent.version;
      console.log(`No direct version found. Using parent version: ${version}`);
   } else {
      console.error('Error: Could not find version in pom.xml or parent');
      process.exit(1);
   }
   console.log(`Found version in pom.xml: ${version}`);

   // Handle -SNAPSHOT replacement if buildNumber is provided
   if (buildNumber && version.includes('-SNAPSHOT')) {
      // According to semver, pre-release versions should be formatted as: X.Y.Z-beta.N
      version = version.replace('-SNAPSHOT', `-beta.${buildNumber}`);
      console.log(`Updated version to: ${version} (replaced -SNAPSHOT with -beta.${buildNumber})`);
   } else if (buildNumber && !version.includes('-SNAPSHOT')) {
      console.log(`Build number provided but version ${version} is not a SNAPSHOT - keeping version as is`);
   }

   // Read package.json
   const packagePath = path.resolve(process.cwd(), 'package.json');
   const packageContent = fs.readFileSync(packagePath, 'utf8');
   const packageData = JSON.parse(packageContent);

   // Update version
   const oldVersion = packageData.version;
   packageData.version = version;

   // Write updated package.json
   fs.writeFileSync(
      packagePath,
      JSON.stringify(packageData, null, 2) + '\n',
      'utf8'
   );

   console.log(`Updated package.json version from ${oldVersion} to ${version}`);

} catch (error) {
   console.error(`Error: ${error.message}`);
   process.exit(1);
}
