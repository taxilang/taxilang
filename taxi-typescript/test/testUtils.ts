import {SchemaHelper} from "../src/schemaHelper";
import {SchemaGenerator, SchemaGeneratorOptions} from "../src/schemaGenerator";
import * as ts from "typescript";

export function schemaFromFile(fileName: string): SchemaHelper {
   return new SchemaHelper(new SchemaGenerator().addSources([fileName]).generate());
}

export function schemaFromSrc(src: string): SchemaHelper {

   let outputs: any[] = [];
   let compilerHost: ts.CompilerHost = {
      getSourceFile: function (filename: any, languageVersion: any) {
         if (filename === "file.ts")
            return ts.createSourceFile(filename, src, ts.ScriptTarget.ESNext);
         if (filename === "lib.d.ts")
            return ts.createSourceFile(filename, "", ts.ScriptTarget.ESNext);
      },
      readFile: function (filename) {
         if (filename === "file.ts") return src;
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
      getCanonicalFileName: function (filename: string) {
         return filename;
      },
      getCurrentDirectory: function () {
         return "";
      },
      getNewLine: function () {
         return "\n";
      },
      fileExists(fileName: string): boolean {
         return fileName === "file.ts" || fileName == "lib.d.ts"
      }
   };
   let options = new SchemaGeneratorOptions();
   options.compilerHost = compilerHost;
   return new SchemaHelper(new SchemaGenerator(options).addSources(["file.ts"]).generate());
}

