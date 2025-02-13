import chokidar from 'chokidar';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import * as moduleUnderTest from '../src/codeGen';
import * as loadConfigModule from '../src/loadConfig';
import * as processFilesModule from '../src/processFiles';
import * as resolveFilesModule from '../src/resolveFiles';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('codeGen tests', () => {
  it('should initialize processing and watching when "watch" is true', async () => {
    // Mock the loadConfig to return a valid config with watch enabled
    const mockConfig = {
      'documents': [
        './**/*.{js,jsx,ts,tsx}',
      ],
      'taxiConf': '../taxi/taxi.conf',
      'outputDir': './src/generated',
      'taxiSourcePath': 'src/**/*.taxi',
      'watch': true,
      'orbitalServerUrl': 'http://localhost:9022/'
    }
    vi.spyOn(loadConfigModule, 'default').mockResolvedValueOnce(mockConfig);

    // Mock resolveFiles to return a dummy file list
    vi.spyOn(resolveFilesModule, 'default').mockResolvedValueOnce(['file1.taxiql', 'file2.taxiql']);

    // Mock processFiles to just resolve
    vi.spyOn(processFilesModule, 'default').mockResolvedValueOnce(undefined);

    // Mock chokidar.watch to avoid interacting with the file system
    const watchMock = vi.fn().mockReturnValue({
      on: vi.fn().mockReturnThis(), // Mock the .on method to chain
    });
    vi.spyOn(chokidar, 'watch').mockImplementation(watchMock);

    // Spy on console.log to verify log output
    const consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {
    });

    // Run the function
    await moduleUnderTest.startProcessingAndWatching();

    // Assertions

    // Check that loadConfig was called
    expect(loadConfigModule.default).toHaveBeenCalledTimes(1);

    // Check that resolveFiles was called with the correct arguments
    expect(resolveFilesModule.default).toHaveBeenCalledWith(
      ['./**/*.{js,jsx,ts,tsx}'],
      expect.objectContaining(mockConfig)
    );

    // Check that processFiles was called with resolved files and config
    expect(processFilesModule.default).toHaveBeenCalledWith(['file1.taxiql', 'file2.taxiql'], expect.anything());

    // Check that chokidar.watch was called (since watch is true)
    expect(chokidar.watch).toHaveBeenCalledTimes(1);

    // Verify that the log statements are printed correctly
    expect(consoleLogSpy).toHaveBeenCalledWith('config:', expect.objectContaining(mockConfig));
    expect(consoleLogSpy).toHaveBeenCalledWith('Watching for file changes...');
  });

  it('should not start watching if "watch" is false', async () => {
    // Mock the loadConfig to return a config with watch set to false
    vi.spyOn(loadConfigModule, 'default').mockResolvedValueOnce({
      documents: ['src/**/*.taxiql'],
      watch: false,
      outputDir: 'output/',
      taxiConf: 'taxi-conf',
      taxiSourcePath: 'src/taxi-source/',
    });

    // Mock resolveFiles and processFiles to resolve with dummy data
    vi.spyOn(resolveFilesModule, 'default').mockResolvedValueOnce(['file1.taxiql']);
    vi.spyOn(processFilesModule, 'default').mockResolvedValueOnce(undefined);

    // Mock chokidar.watch to ensure it's not called
    const watchMock = vi.fn();
    vi.spyOn(chokidar, 'watch').mockImplementation(watchMock);

    // Spy on console.log to verify log output
    const consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {
    });

    // Run the function
    await moduleUnderTest.startProcessingAndWatching();

    // Assertions

    // Check that loadConfig was called
    expect(loadConfigModule.default).toHaveBeenCalledTimes(1);

    // Ensure chokidar.watch was not called since watch is false
    expect(watchMock).not.toHaveBeenCalled();

    // Verify the correct log message
    expect(consoleLogSpy).toHaveBeenCalledWith('config:', expect.objectContaining({
      documents: ['src/**/*.taxiql'],
      watch: false,
    }));
    expect(consoleLogSpy).toHaveBeenCalledWith('File watching is disabled, exiting');
  });

  it('should throw an error if "documents" is not an array', async () => {
    // Mock the loadConfig to return invalid config (documents is not an array)
    vi.spyOn(loadConfigModule, 'default').mockResolvedValueOnce({
      // @ts-ignore
      'documents': 'not-an-array', // Invalid config
      'taxiConf': '../taxi/taxi.conf',
      'outputDir': './src/generated',
      'taxiSourcePath': 'src/**/*.taxi',
      'watch': true,
      'orbitalServerUrl': 'http://localhost:9022/'
    });

    // Spy on console.error to catch the error log
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {
    });

    // Run the function
    await moduleUnderTest.startProcessingAndWatching();

    // Ensure the error log is printed
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      expect.stringContaining('Error initializing processing and watching:'),
      expect.objectContaining({
        message: expect.stringContaining('Invalid configuration: "documents" must be an array.')
      })
    );
  });
})
