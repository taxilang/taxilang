import * as ts from 'typescript';
import { Type } from "./schema";
export declare class TypeGenerator {
    readonly nodes: ts.Node[];
    constructor(nodes: ts.Node[]);
    private constructedTypes;
    readonly types: Type[];
    generate(node: ts.ObjectTypeDeclaration): Type;
    private generateField;
    private generateFieldFromProperty;
    private lookupType;
    private getOrBuildType;
    private getPropertyName;
    private getName;
    private getObjectTypeName;
    private createTypeAlias;
    private getExplicitName;
    private hasExplicitName;
}
