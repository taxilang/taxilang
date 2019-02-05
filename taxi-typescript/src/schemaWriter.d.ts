import { TaxiDocument } from "./schema";
export declare class SchemaWriter {
    generateSchemas(docs: TaxiDocument[]): string[];
    private generateSchema;
    private generateTypeDeclaration;
    private generateObjectTypeDeclaration;
    private generateFieldDeclaration;
    private typeAsTaxi;
    private getInheritenceString;
    private generateTypeAliasDeclaration;
    private generateServiceDeclaration;
    private generateOperationDeclaration;
    private quoteIfNeeded;
    private isNumber;
    private generateAnnotations;
}
