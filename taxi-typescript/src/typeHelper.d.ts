import * as ts from "typescript";
export declare type NodePredicate = (value: ts.Node) => boolean;
export declare class TypeHelper {
    readonly nodes: ts.Node[];
    constructor(nodes: ts.Node[]);
    hasJsDocTag(tagName: string): NodePredicate;
    getTypesWithJsDocTag(tagName: string): ts.ObjectTypeDeclaration[];
    getTypeDeclarations(filter: NodePredicate): ts.ObjectTypeDeclaration[];
    readonly objectTypeDeclarations: ts.ObjectTypeDeclaration[];
    findType(typescriptTypeName: string): ts.ObjectTypeDeclaration;
    getNameFromIdentifier(identifier: ts.EntityName): string;
    getName(typeWithName: ts.DeclarationStatement, considerExplicitNameTags?: boolean): string;
    getObjectTypeName(node: ts.ObjectTypeDeclaration, considerExplicitNameTags?: boolean): string;
    private getExplicitName;
    private hasExplicitName;
    getJsDocTags(node: ts.Node, tagName: string): ts.JSDocTag[];
    isJsDocContainer(node: any): node is JsDocContainer;
    getMembersWithJsDocTag(node: ts.ObjectTypeDeclaration, tagName: string): ts.ClassElement[] | ts.TypeElement[];
}
export interface JsDocContainer {
    jsDoc: ts.JSDoc[];
}
