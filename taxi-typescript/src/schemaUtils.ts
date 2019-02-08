import {SchemaHelper} from "./schemaHelper";
import {SchemaGenerator, SchemaGeneratorOptions} from "./schemaGenerator";
import * as ts from "typescript";

export function schemaFromFile(fileName: string, options: SchemaGeneratorOptions = new SchemaGeneratorOptions()): SchemaHelper {
   if (!options) {
      options = new SchemaGeneratorOptions()
   }
   let schemaGenerator = new SchemaGenerator(options).addSources([fileName]);
   return new SchemaHelper(schemaGenerator, schemaGenerator.generate());
}

export function schemaFromSrc(src: string, filename: string = "file.ts", options: SchemaGeneratorOptions = new SchemaGeneratorOptions()): SchemaHelper {

   let outputs: any[] = [];
   options.compilerHost = {
      getSourceFile: function (fileNameToGet: any, languageVersion: any) {
         if (fileNameToGet === filename)
            return ts.createSourceFile(fileNameToGet, src, ts.ScriptTarget.ESNext);
         if (fileNameToGet === "lib.d.ts")
            return ts.createSourceFile(fileNameToGet, "", ts.ScriptTarget.ESNext);
      },
      readFile: function (fileNameToRead) {
         if (fileNameToRead === filename) return src;
         return undefined
      },
      writeFile: function (name: string, text: string, writeByteOrderMark: boolean) {
         outputs.push({name: name, text: text, writeByteOrderMark: writeByteOrderMark});
      },
      getDefaultLibFileName: function () {
         return "lib.d.ts";
      },
      useCaseSensitiveFileNames: function () {
         return false;
      },
      getCanonicalFileName: function (inputFilename: string) {
         return inputFilename;
      },
      getCurrentDirectory: function () {
         return "";
      },
      getNewLine: function () {
         return "\n";
      },
      fileExists(inputFilename: string): boolean {
         return inputFilename === filename || inputFilename == "lib.d.ts"
      }
   };
   let schemaGenerator = new SchemaGenerator(options).addSources([filename]);
   return new SchemaHelper(schemaGenerator, schemaGenerator.generate());
}

