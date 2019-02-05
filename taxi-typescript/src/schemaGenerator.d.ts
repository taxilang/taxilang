import * as ts from "typescript";
import * as taxi from "./schema";
import { TypeMapperFactory } from "./typeGenerator";
import { ServiceGeneratorFactory } from "./serviceGenerator";
export declare class SchemaGeneratorOptions {
    typeMapperFactory: TypeMapperFactory;
    serviceMapperFactory: ServiceGeneratorFactory;
    compilerHost?: ts.CompilerHost;
}
export declare class SchemaGenerator {
    private readonly sourceFiles;
    private readonly typeMapperFactory;
    private readonly serviceMapperFactory;
    private readonly compilerHost?;
    constructor(options?: SchemaGeneratorOptions);
    addSources(entryFiles: string[]): SchemaGenerator;
    generate(): taxi.TaxiDocument;
    private createTypeHelper;
}
