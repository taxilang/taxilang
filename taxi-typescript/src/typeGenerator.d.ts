import * as ts from 'typescript';
import { Type, TypeKind } from "./types";
import { TypeHelper } from "./typeHelper";
export interface TypeMapperFactory {
    build(typeHelper: TypeHelper): TypeMapper;
}
export interface TypeMapper {
    readonly types: Type[];
    getTypeOrDefault(node: ts.Node | undefined, defaultType: Type): Type;
}
export declare class DefaultTypeMapperFactory implements TypeMapperFactory {
    build(typeHelper: TypeHelper): TypeMapper;
}
export declare class DefaultTypeGenerator implements TypeMapper {
    readonly typeHelper: TypeHelper;
    constructor(typeHelper: TypeHelper);
    private constructedTypes;
    private build;
    getTypeOrDefault(node: ts.Node, defaultType: Type): Type;
    readonly types: Type[];
    generate(node: ts.ObjectTypeDeclaration): Type;
    private generateField;
    private generateFieldFromProperty;
    private lookupType;
    private getOrBuildType;
    private getPropertyName;
    private createTypeAlias;
    private isDeclaredDataType;
}
declare class PrimitiveType implements Type {
    readonly declaration: string;
    kind: TypeKind;
    constructor(declaration: string);
    readonly qualifiedName: string;
}
export declare class Primitives {
    static BOOLEAN: PrimitiveType;
    static STRING: PrimitiveType;
    static INTEGER: PrimitiveType;
    static DECIMAL: PrimitiveType;
    static LOCAL_DATE: PrimitiveType;
    static TIME: PrimitiveType;
    static INSTANT: PrimitiveType;
    static ARRAY: PrimitiveType;
    static ANY: PrimitiveType;
    static DOUBLE: PrimitiveType;
    static VOID: PrimitiveType;
    static primitives: Map<ts.SyntaxKind, PrimitiveType>;
    static initialize(): void;
    static forNodeKind(nodeKind: ts.SyntaxKind): PrimitiveType | undefined;
}
export {};
