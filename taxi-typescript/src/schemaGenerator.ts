import * as ts from "typescript";
import * as taxi from "./schema";
import {TaxiDocument} from "./schema";
import {TypeGenerator} from "./typeGenerator";

export class SchemaGenerator {
   public readonly typeChecker: ts.TypeChecker;
   private readonly program: ts.Program;
   private readonly nodes: ts.Node[] = [];

   constructor(entryFiles: string[], compilerHost?: ts.CompilerHost) {
      this.program = ts.createProgram(entryFiles, {}, compilerHost);
      this.typeChecker = this.program.getTypeChecker()
   }

   public generate(): taxi.TaxiDocument {
      this.program.getSourceFiles().forEach(sourceFile => {
         if (!sourceFile.isDeclarationFile) {
            ts.forEachChild(sourceFile, node => {
               this.nodes.push(node);
            })
         }
      });

      const typeMapper = new TypeGenerator(this.nodes);

      const types =
         this.nodes.filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
            .map(type => typeMapper.generate(<ts.ObjectTypeDeclaration>type));

      return <TaxiDocument>{
         types: typeMapper.types,
         policies: [],
         services: []
      }
   }
}
