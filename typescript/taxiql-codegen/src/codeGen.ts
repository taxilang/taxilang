#!/usr/bin/env node
import chokidar from 'chokidar';
import loadConfig from './loadConfig.js';
import processFiles from './processFiles.js';
import resolveFiles from './resolveFiles.js';

export async function startProcessingAndWatching() {
  try {
    const config = await loadConfig();
    const { documents, watch } = config

    console.log('config:', config)

    if (!Array.isArray(documents)) {
      throw new Error('Invalid configuration: "documents" must be an array.');
    }

    // Files that we want to extract taxiql`` from
    let resolvedTaxiQlSourcePaths = await resolveFiles(documents, config)

    // Initial processing of files
    await processFiles(resolvedTaxiQlSourcePaths, config);

    if (watch) {
      // Set up file watcher only if "watch" is true
      const watcher = chokidar.watch(resolvedTaxiQlSourcePaths, {
        persistent: true,
        ignoreInitial: true, // Skip initial add events
        //usePolling: true
      });

      // Watch for file changes and reprocess
      watcher
        .on('change', async (filePath) => {
          console.log(`File changed: ${filePath}`);
          await processFiles(resolvedTaxiQlSourcePaths, config);
        })
        .on('add', async (filePath) => {
          console.log(`File added: ${filePath}`);
          resolvedTaxiQlSourcePaths = await resolveFiles(documents, config)
          await processFiles(resolvedTaxiQlSourcePaths, config);
        })
        .on('unlink', async (filePath) => {
          console.log(`File removed: ${filePath}`);
          resolvedTaxiQlSourcePaths = await resolveFiles(documents, config)
          await processFiles(resolvedTaxiQlSourcePaths, config);
        });

      console.log('Watching for file changes...');
    } else {
      console.log('File watching is disabled, exiting');
    }
  } catch (error) {
    console.error('Error initializing processing and watching:', error);
  }
}

// Start the process
startProcessingAndWatching();
