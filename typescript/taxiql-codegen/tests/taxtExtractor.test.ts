import fs from 'fs';
import path from 'path';
import taxiExtractor from '../src/taxiExtractor'; // Adjust to your actual file path
import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('taxiExtractor', () => {
  beforeEach(() => {
    vi.clearAllMocks(); // Clear mocks before each test
  });

  it('should correctly extract and clean taxiQl queries', async () => {
    // Mock the file reading functionality to return specific test content
    vi.spyOn(fs.promises, 'readFile').mockResolvedValueOnce(`
const QUERY = taxiQl(\`
  query FindFilmsTS {
    find { Film[] }
  }
\`);
const QUERY2 = taxiQl(\`
  /* a comment */
  query FindFilmsWithReviewTS {
    // a different comment
    find { Film[] } as {
      [[ some typedocs ]]
      title333: Title
      /*
       * a multiline comment
       * as well!
       */
      reviewScore : ReviewScore
      review : ReviewText
    }[]
  }
\`);
`);

    // Run taxiExtractor on mock file content
    const result = await taxiExtractor(['mock-file.ts']);

    // Assertions: Check that the extracted queries are correctly processed and cleaned
    expect(result).toEqual([
      '\n  query FindFilmsTS {\n    find { Film[] }\n  }\n',
      '\n  /* a comment */\n  query FindFilmsWithReviewTS {\n    // a different comment\n    find { Film[] } as {\n      [[ some typedocs ]]\n      title333: Title\n      /*\n       * a multiline comment\n       * as well!\n       */\n      reviewScore : ReviewScore\n      review : ReviewText\n    }[]\n  }\n',
    ]);

    // Verify that readFile was called with the correct path
    expect(fs.promises.readFile).toHaveBeenCalledWith(path.resolve('mock-file.ts'), 'utf8');
  });

  it('should correctly handle files with comments', async () => {
    // Mock the file reading functionality to return a file with comments
    vi.spyOn(fs.promises, 'readFile').mockResolvedValueOnce(`
// This is a comment
const QUERY = taxiQl(\`
  query FindFilmsTS {
    find { Film[] }
  }
\`);

/* Top level comment */
const QUERY2 = taxiQl(\`
  query FindFilmsWithReviewTS {
    find { Film[] } as {
      title333: Title
      reviewScore : ReviewScore
      review : ReviewText
    }[]
  }
\`);
`);

    // Run taxiExtractor on the mock file
    const result = await taxiExtractor(['mock-file-with-comments.ts']);

    // Assertions: The extracted queries should still be cleaned of comments and extra whitespace
    expect(result).toEqual([
      '\n  query FindFilmsTS {\n    find { Film[] }\n  }\n',
      '\n  query FindFilmsWithReviewTS {\n    find { Film[] } as {\n      title333: Title\n      reviewScore : ReviewScore\n      review : ReviewText\n    }[]\n  }\n',
    ]);
  });

  it('should handle empty files without errors', async () => {
    // Mock an empty file
    vi.spyOn(fs.promises, 'readFile').mockResolvedValueOnce('');

    const result = await taxiExtractor(['empty-file.ts']);

    // The result should be an empty array since no queries should be extracted
    expect(result).toEqual([]);
  });

  it('should handle files with .taxiql extension', async () => {
    // Mock reading a .taxiql file content
    vi.spyOn(fs.promises, 'readFile').mockResolvedValueOnce('query FindFilmsTS { find { Film[] } }');

    const result = await taxiExtractor(['mock-file.taxiql']);

    // The query should be directly extracted and cleaned up
    expect(result).toEqual([
      'query FindFilmsTS { find { Film[] } }',
    ]);
  });
});
