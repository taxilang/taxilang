import {SchemaHelper} from "../src/schemaHelper";
import {expect} from "chai";
import {SchemaGenerator} from "../src/schemaGenerator";
import * as ts from 'typescript'
import {ObjectType} from "../src/types";

describe("Taxi model generator", () => {


   describe("kitchen sink generation", () => {
      const taxiDoc = schemaFromFile("./test/testModels.ts");
      it("should generate a model for a user type correctly", () => {
         expect(taxiDoc.type("Client")).not.to.be.undefined;
         let clientType = taxiDoc.type("Client") as ObjectType;

         expect(clientType.field("clientId").type.qualifiedName).to.equal("lang.taxi.String");
         expect(clientType.field("personName").type.qualifiedName).to.equal("foo.PersonName");
         expect(clientType.field("age").type.qualifiedName).to.equal("lang.taxi.Int");
         expect(clientType.field("active").type.qualifiedName).to.equal("lang.taxi.Boolean");
         expect(clientType.field("referrer").type.qualifiedName).to.equal("Client")
      });

   });

   it("should generate a model with an explicit name correctly", () => {
      const schema = schemaFromSrc(`
/**
 * @DataType("foo.Client")
 */
interface Client {}      
`);
      expect(schema.type("foo.Client")).to.not.be.undefined;
   });

   it("should use the class name if no name is provided", () => {
      const schema = schemaFromSrc(`
/**
 * @DataType
 */
interface Client {}
`);
      expect(schema.type("Client")).to.not.be.undefined;
   });

   // TODO : Should probably just skip it if the @DataType tag isn't present?
   it("should use the class name if no @DataType tag is present", () => {
      const schema = schemaFromSrc(`
interface Client {}
`);
      expect(schema.type("Client")).to.not.be.undefined;
   });

   it("should map inheritence correctly", () => {
      const schema = schemaFromSrc(`
interface Foo {}
/**
 * @DataType("test.Bar")
 */
interface Bar {}
interface Baz extends Foo, Bar
`);
      let type = schema.type("Baz") as ObjectType;
      expect(type.inheritsFrom).to.have.lengthOf(2);
      expect(type.inheritsFrom.some(t => t.qualifiedName == "Foo")).to.be.true;
      expect(type.inheritsFrom.some(t => t.qualifiedName == "test.Bar")).to.be.true
   })
});

export function schemaFromFile(fileName: string): SchemaHelper {
   return new SchemaHelper(new SchemaGenerator([fileName]).generate());
}

function schemaFromSrc(src: string): SchemaHelper {

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
   return new SchemaHelper(new SchemaGenerator(["file.ts"], compilerHost).generate());
}

