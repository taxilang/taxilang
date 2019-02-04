import * as ts from "typescript";
import * as taxi from "./schema";
export declare class SchemaGenerator {
    readonly typeChecker: ts.TypeChecker;
    private readonly program;
    private readonly nodes;
    constructor(entryFiles: string[], compilerHost?: ts.CompilerHost);
    generate(): taxi.TaxiDocument;
}
