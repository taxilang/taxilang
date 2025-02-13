import fs from 'fs';
import path from 'path';

const taxiExtractor = async (filePaths: string[]) => {
   const queries: string[] = [];

   // Extract and process the file contents
   await readFiles(filePaths).then((fileContents: Record<string, string>) => {
      for (const [filePath, content] of Object.entries(fileContents)) {
         if (/(\.js|\.jsx|\.ts|\.tsx)$/i.test(filePath)) {
            // Extract `taxiQl` tagged template literals
            const regex = /taxiQl\s*\(\s*`([\s\S]*?)`\s*\)/g;

            let match;
            while ((match = regex.exec(content)) !== null) {
               queries.push(match[1]);
            }
         } else if (filePath.endsWith('.taxiql')) {
            // For .taxiql files, process content directly
            queries.push(content);
         }
      }
   });

   const returnVal = queries.join('\n')
   console.log('Extracted Queries:\n', returnVal);

   return queries
};

const readFiles = async (paths: string[]) => {
   const results: Record<string, string> = {};
   for (const filePath of paths) {
      // Resolve the path to handle relative imports
      const resolvedPath = path.resolve(filePath);
      try {
         const content = await fs.promises.readFile(resolvedPath, 'utf8');
         results[filePath] = content; // Store content by file path
      } catch (error: any) {
         console.error(`Error reading file at ${resolvedPath}:`, error.message);
      }
   }
   return results;
};

export default taxiExtractor
