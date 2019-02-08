import * as ts from "typescript";
import * as _ from "lodash";

export type NodePredicate = (value: ts.Node) => boolean;

export class TypescriptTypeName {
   constructor(readonly typeName: string, readonly declaringModule?: string) {
   }

   /**
    * returns true if type names match, and the module name passed is
    * a child of the declaring module.
    *
    * Used where we don't care which file within a package declares a type - just that the type is within the
    * package.  (ie; @types/aws-lambda vs @types/aws-lambda/index)
    * @param moduleName
    * @param typeName
    */
   nameLooksCloseTo(moduleName: string, typeName: string): boolean {
      return this.moduleIsChildOf(moduleName) && typeName == this.typeName
   }

   moduleIsChildOf(name: string): boolean {
      if (!this.declaringModule) return false;
      return this.declaringModule.includes(name)
   }
}


export class TypeHelper {
   private typeChecker: ts.TypeChecker;

   constructor(readonly nodes: ts.Node[], private program: ts.Program) {
      this.typeChecker = program.getTypeChecker();
   }

   getFullyQualifiedName(node: ts.Node): TypescriptTypeName {
      let type = this.typeChecker.getTypeAtLocation(node);
      if (!type) {
         throw new Error("Node is not a type")
      }

      let symbol = type.aliasSymbol;
      if (!symbol) {
         throw new Error("Type does not declare an aliasSymbol. This is not yet handled, but could be.")
      }
      let declaringModuleName: string | undefined;
      if (symbol && symbol.hasOwnProperty("parent")) {
         let parent = (<any>symbol).parent as ts.Symbol;

         // The idea is that node_modules may be nested, so pick the last one.
         let declaringModules = parent.escapedName.toString().split("node_modules");
         let declaringModule = declaringModules[declaringModules.length - 1];
         if (declaringModule.startsWith("/")) {
            declaringModuleName = declaringModule.substr(1)
         } else {
            declaringModuleName = declaringModule;
         }
      }

      return new TypescriptTypeName(symbol.escapedName.toString(), declaringModuleName);
   }

   hasJsDocTag(tagName: string, caseSensitive: boolean = false): NodePredicate {
      return (node: ts.Node) => {
         return this.getJsDocTags(node, tagName, caseSensitive).length > 0;
      }
   }

   getTypesWithJsDocTag(tagName: string, caseSensitive: boolean = false): ts.ObjectTypeDeclaration[] {
      return this.getTypeDeclarations(this.hasJsDocTag(tagName, caseSensitive));
   }

   getNodesWithJsDocTag(tagName: string): ts.Node[] {
      return this.nodes.filter(this.hasJsDocTag(tagName))
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

   findExportedNode(name: string): ts.Node {
      let parts = name.split(".");
      // Note : I'm sure there are valid scenarios where the length might be different,
      // but need to handle those once I understand them
      if (parts.length !== 2) {
         throw new Error("Expected the name to be in the format of 'foo.bar', with exactly two parts.")
      }
      let [srcFileName, exportName] = parts;
      let matchingNodes = this.program.getSourceFiles()
         .map(srcFile => {
            let srcFileSymbol = this.typeChecker.getSymbolAtLocation(srcFile);
            return (srcFileSymbol && this.stripQuotes(srcFileSymbol.escapedName.toString()) == srcFileName) ? srcFileSymbol : null;
         }).filter(o => o) // filter not null
         .map(srcFileSymbol => {
            if (srcFileSymbol && srcFileSymbol.exports) {
               let srcFileExports: ts.SymbolTable = srcFileSymbol.exports;
               let escapedExportName: ts.__String = exportName as ts.__String;
               return srcFileExports.get(escapedExportName)
            }
            return undefined;
         }).filter(o => o); // filter not undefined

      if (matchingNodes.length === 0) {
         throw new Error(`No symbol with name ${name} was found`)
      } else if (matchingNodes.length > 1) {
         throw new Error(`Found multiple exported symbols with name ${name}`)
      }
      let matchingNode = matchingNodes[0]!;
      // this.typeChecker.getExportSpecifierLocalTargetSymbol(matchingNode.declarations[0])
      let matchingSymbol = this.typeChecker.getAliasedSymbol(matchingNode);
      return matchingSymbol.valueDeclaration;
   }

   private stripQuotes(input: string, quoteChar: string = '"'): string {
      if (input.startsWith(quoteChar) && input.endsWith(quoteChar)) {
         return input.slice(1, -1)
      } else {
         return input
      }
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

   getJsDocTags(node: ts.Node, tagName: string, caseSensitive: boolean): ts.JSDocTag[] {
      if (!this.isJsDocContainer(node)) {
         return [];
      }
      let docs: ts.JSDoc[] = node.jsDoc;

      let docsWithTags: ts.JSDoc[] = docs.filter(doc => doc.tags);
      let tags = _.flatMap(docsWithTags, jsDoc => {
         return jsDoc.tags || []
      })
         .filter(tag => {
            return (caseSensitive)
               ? tag.tagName.escapedText === tagName
               : tag.tagName.escapedText.toString().toLowerCase() === tagName.toLowerCase()
         });
      return tags;
   }

   isJsDocContainer(node: any): node is JsDocContainer {
      return node.hasOwnProperty("jsDoc")
   }

   getMembersWithJsDocTag(node: ts.ObjectTypeDeclaration, tagName: string, caseSensitive: boolean = false): ts.ClassElement[] | ts.TypeElement[] {
      if (ts.isClassLike(node)) {
         return node.members.filter(this.hasJsDocTag(tagName, caseSensitive))
      } else if (ts.isInterfaceDeclaration(node)) {
         return node.members.filter(this.hasJsDocTag(tagName, caseSensitive))
      } else if (ts.isTypeLiteralNode(node)) {
         return node.members.filter(this.hasJsDocTag(tagName, caseSensitive))
      } else {
         throw new Error("Unhandled members type")
      }
   }

}

export interface JsDocContainer {
   jsDoc: ts.JSDoc[]
}
