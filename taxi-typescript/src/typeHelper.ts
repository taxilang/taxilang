import * as ts from "typescript";
import * as _ from "lodash";

export type NodePredicate = (value: ts.Node) => boolean;


export class TypeHelper {
   constructor(readonly nodes: ts.Node[]) {
   }

   hasJsDocTag(tagName: string): NodePredicate {
      return (node: ts.Node) => {
         return this.getJsDocTags(node, tagName).length > 0;
      }
   }

   getTypesWithJsDocTag(tagName: string): ts.ObjectTypeDeclaration[] {
      return this.getTypeDeclarations(this.hasJsDocTag(tagName));
   }

   getTypeDeclarations(filter: NodePredicate): ts.ObjectTypeDeclaration[] {
      return this.nodes
         .filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
         .filter(filter)
         .map(n => n as ts.ObjectTypeDeclaration)
   }

   get objectTypeDeclarations(): ts.ObjectTypeDeclaration[] {
      return this.nodes.filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
         .map(n => n as ts.ObjectTypeDeclaration)
      // .forEach(type => this.generate(<ts.ObjectTypeDeclaration>type));

   }

   findType(typescriptTypeName: string): ts.ObjectTypeDeclaration {
      let typeDeclarationNode = this.nodes.find(node => {
         // Don't consider the @DataType( ... ) name when looking in source, as we're looking for the token, not the compiler type
         if (ts.isInterfaceDeclaration(node) && this.getObjectTypeName(node, false) == typescriptTypeName) return true;
         if (ts.isTypeAliasDeclaration(node) && this.getName(node, false) == typescriptTypeName) return true;
         return false;
      });
      if (!typeDeclarationNode) {
         throw new Error(`No type with name ${typescriptTypeName} is defined`)
      }
      return typeDeclarationNode as ts.ObjectTypeDeclaration

   }

   getNameFromIdentifier(identifier: ts.EntityName): string {
      if (ts.isIdentifier(identifier)) {
         return identifier.escapedText.toString()
      } else if (ts.isQualifiedName(identifier)) {
         return identifier.right.escapedText.toString()
      }
      throw new Error("Unhandled name from identifier case");
   }

   getName(typeWithName: ts.DeclarationStatement, considerExplicitNameTags: boolean = true): string {
      if (considerExplicitNameTags && this.hasExplicitName(typeWithName)) {
         return this.getExplicitName(typeWithName)!
      } else if (typeWithName.name && ts.isIdentifier(typeWithName.name)) {
         return typeWithName.name.escapedText.toString()
      } else if (typeWithName.name && ts.isQualifiedName(typeWithName.name)) {
         return typeWithName.name.right.escapedText.toString()
      } else {
         throw new Error("Unable to get name from node with type " + typeWithName)
      }

   }

   getObjectTypeName(node: ts.ObjectTypeDeclaration, considerExplicitNameTags: boolean = true): string {
      if (ts.isInterfaceDeclaration(node)) {
         return this.getName(node, considerExplicitNameTags)
      } else if (ts.isClassLike(node)) {
         if (node.name) {
            return this.getName(node as ts.DeclarationStatement, considerExplicitNameTags)
         } else {
            throw new Error("Classes without names are not supported")
         }
      } else {
         throw new Error("Unhandled type declaration : " + node.kind)
      }
   }

   private getExplicitName(typeName: ts.NamedDeclaration): string | undefined {
      let jsDocs: ts.JSDoc[];
      let container: any = typeName;
      switch (true) {
         case container.hasOwnProperty("jsDoc"):
            jsDocs = container.jsDoc;
            break;
         // case container.parent && container.parent.hasOwnProperty("jsDoc"):
         //    jsDocs = container.parent.jsDoc;
         //    break;
         default:
            return undefined;
      }
      let dataTypeTags = jsDocs
         .filter(doc => doc.tags)
         .map(doc => {
            return doc.tags!.find(tag => tag.tagName.escapedText === "DataType");
         });

      if (!dataTypeTags) return undefined;
      let dataTypeTag = dataTypeTags[0];

      if (dataTypeTag && dataTypeTag.comment) {
         // const name = dataTypeTag.comment.match(QUOTED_VALUE_REGEX);

         // Only take the text up until the first non whitespace char (or newline)
         return dataTypeTag.comment.trim().split(/\s+/)[0];
      } else {
         return undefined;
      }
   }

   private hasExplicitName(typeName: ts.NamedDeclaration): boolean {
      return this.getExplicitName(typeName) !== undefined
   }

   getJsDocTags(node: ts.Node, tagName: string): ts.JSDocTag[] {
      if (!this.isJsDocContainer(node)) {
         return [];
      }
      let docs: ts.JSDoc[] = node.jsDoc;

      let docsWithTags: ts.JSDoc[] = docs.filter(doc => doc.tags);
      let tags = _.flatMap(docsWithTags, jsDoc => {
         return jsDoc.tags || []
      })
         .filter(tag => tag.tagName.escapedText === tagName);
      return tags;
   }

   isJsDocContainer(node: any): node is JsDocContainer {
      return node.hasOwnProperty("jsDoc")
   }

   getMembersWithJsDocTag(node: ts.ObjectTypeDeclaration, tagName: string): ts.ClassElement[] | ts.TypeElement[] {
      if (ts.isClassLike(node)) {
         return node.members.filter(this.hasJsDocTag(tagName))
      } else if (ts.isInterfaceDeclaration(node)) {
         return node.members.filter(this.hasJsDocTag(tagName))
      } else if (ts.isTypeLiteralNode(node)) {
         return node.members.filter(this.hasJsDocTag(tagName))
      } else {
         throw new Error("Unhandled members type")
      }
   }
}

export interface JsDocContainer {
   jsDoc: ts.JSDoc[]
}
