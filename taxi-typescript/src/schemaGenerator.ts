import * as ts from "typescript";
import * as taxi from "./schema";
import {TaxiDocument} from "./schema";
import {DefaultTypeMapperFactory, TypeMapperFactory} from "./typeGenerator";
import {DefaultServiceGeneratorFactory, ServiceGeneratorFactory} from "./serviceGenerator";
import {TypeHelper} from "./typeHelper";

export class SchemaGeneratorOptions {
   typeMapperFactory: TypeMapperFactory = new DefaultTypeMapperFactory();
   serviceMapperFactory: ServiceGeneratorFactory = new DefaultServiceGeneratorFactory();
   compilerHost?: ts.CompilerHost;
}

export class SchemaGenerator {
   // public readonly typeChecker: ts.TypeChecker;
   // private readonly program: ts.Program;
   // private readonly nodes: ts.Node[] = [];

   private readonly sourceFiles: string[] = [];
   private readonly typeMapperFactory: TypeMapperFactory;
   private readonly serviceMapperFactory: ServiceGeneratorFactory;
   private readonly compilerHost?: ts.CompilerHost;

   constructor(options: SchemaGeneratorOptions = new SchemaGeneratorOptions()) {
      this.typeMapperFactory = options.typeMapperFactory;
      this.serviceMapperFactory = options.serviceMapperFactory;
      this.compilerHost = options.compilerHost;
   }

   // constructor(entryFiles: string[], compilerHost?: ts.CompilerHost) {
   //    this.program = ts.createProgram(entryFiles, {}, compilerHost);
   //    // this.typeChecker = this.program.getTypeChecker()
   // }

   addSources(entryFiles: string[]): SchemaGenerator {
      entryFiles.forEach(f => this.sourceFiles.push(f));
      return this;
   }

   public generate(): taxi.TaxiDocument {
      let typeHelper = this.createTypeHelper();
      const typeMapper = this.typeMapperFactory.build(typeHelper);

      const serviceMapper = this.serviceMapperFactory.build(typeHelper, typeMapper);

      return <TaxiDocument>{
         types: typeMapper.types,
         policies: [],
         services: serviceMapper.services
      }
   }

   private createTypeHelper(): TypeHelper {
      let program = ts.createProgram(this.sourceFiles, {}, this.compilerHost);
      let nodes: ts.Node[] = [];
      program.getSourceFiles().forEach(sourceFile => {
         if (!sourceFile.isDeclarationFile) {
            ts.forEachChild(sourceFile, node => {
               nodes.push(node);
            })
         }
      });
      return new TypeHelper(nodes);
   }
}
