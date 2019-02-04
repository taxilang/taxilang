import * as ts from 'typescript';
import {Field, Type, TypeAliasDefinition} from "./schema";
import {ObjectType, TypeAlias, UserType} from "./types";
import * as _ from 'lodash';

// Regex for matching values inside a quotation.
// https://stackoverflow.com/a/171499/59015
// I hate regex.
const QUOTED_VALUE_REGEX = new RegExp(/(["'])(\\?.)*?\1/g);

export class TypeGenerator {
   constructor(readonly nodes: ts.Node[]) {

   }

   private constructedTypes: Map<string, UserType<any, any>> = new Map();

   get types(): Type[] {
      return Array.from(this.constructedTypes.values());
   }

   generate(node: ts.ObjectTypeDeclaration): Type {
      // TODO : For now, assume all user types
      if (ts.isTypeAliasDeclaration(node)) {
         const typeAlias = this.createTypeAlias(node);
         this.constructedTypes.set(typeAlias.qualifiedName, typeAlias);
         return typeAlias
      }
      let typescriptDeclaredName = this.getObjectTypeName(node, false);
      let typeNameInTaxi = this.getObjectTypeName(node, true);
      let type = new ObjectType(typeNameInTaxi, null);

      // Store the type now, undefined, so that other types may reference it (or it may reference itself)
      // Store the type using it's name as it appears in ts, not as it will be generated in taxi
      this.constructedTypes.set(typescriptDeclaredName, type);

      let fields: Field[] = Optional.values((node.members as ts.NodeArray<any>)
         .map((member: any) => this.generateField(member)));

      let heritageClauses: ts.HeritageClause[] = (<any>node).heritageClauses || [];
      let inheritetedTypes = _.flatMap(heritageClauses, clause => clause.types.map(inheitedType =>
         this.getOrBuildType(this.getName(inheitedType.expression as ts.Identifier)) as ObjectType
      ));
      type.definition = {
         annotations: [], // TODO
         fields: fields,
         inheritsFrom: inheritetedTypes, // TODO,
         modifiers: [] // TODO
      };

      return type;
   }

   private generateField(member: any): Optional<Field> {
      switch (member.kind) {
         case ts.SyntaxKind.PropertySignature:
            return Optional.of(this.generateFieldFromProperty(member));
         case ts.SyntaxKind.SetAccessor:
         case ts.SyntaxKind.GetAccessor:
            return Optional.empty();
         // ignore, I think these are alreaddy handled?
         default:
            throw new Error("Unhandled")

      }
   }

   private generateFieldFromProperty(member: ts.PropertySignature): Field {
      const type = (member.type) ? this.lookupType(member.type) : Primitives.ANY;
      const name = this.getPropertyName(member.name);
      return <Field>{
         name: name,
         type: type,
         annotations: [], // TODO
         constraints: [], // TODO
         description: "unused",
         nullable: false // TODO
      };
      //
      throw ""

   }

   private lookupType(type: ts.TypeNode): Type {
      if (Primitives.forNodeKind(type.kind)) {
         return Primitives.forNodeKind(type.kind) as Type
      } else if (ts.isTypeReferenceNode(type)) {
         let typeName = this.getName(type.typeName);
         return this.getOrBuildType(typeName)
      } else {
         console.log("Unhanled type.kind:  " + type.kind);
         return Primitives.ANY; // TODO
      }
   }

   private getOrBuildType(typescriptTypeName: string): Type {
      if (this.constructedTypes.has(typescriptTypeName)) {
         return this.constructedTypes.get(typescriptTypeName) as Type
      } else {
         let typeDeclarationNode = this.nodes.find(node => {
            // Don't consider the @DataType( ... ) name when looking in source, as we're looking for the token, not the compiler type
            if (ts.isInterfaceDeclaration(node) && this.getObjectTypeName(node, false) == typescriptTypeName) return true;
            if (ts.isTypeAliasDeclaration(node) && this.getName(node.name, false) == typescriptTypeName) return true;
            return false;
         });
         if (!typeDeclarationNode) {
            throw new Error(`No definition for type ${typescriptTypeName} found, cannot construct typedef.`)
         }
         return this.generate(typeDeclarationNode as ts.ObjectTypeDeclaration)
      }
   }

   private getPropertyName(name: ts.PropertyName): string {
      if (ts.isIdentifier(name)) {
         return name.escapedText.toString()
      } else if (ts.isStringLiteral(name) || ts.isNumericLiteral(name)) {
         return name.text
      } else {
         throw Error("Unadanled name type")
      }
   }

   private getName(typeName: ts.EntityName, considerExplicitNameTags: boolean = true): string {
      if (considerExplicitNameTags && this.hasExplicitName(typeName)) {
         return this.getExplicitName(typeName)!
      }
      if (ts.isIdentifier(typeName)) {
         return typeName.escapedText.toString()
      } else if (ts.isQualifiedName(typeName)) {
         return typeName.right.escapedText.toString()
      } else {
         throw new Error("Unable to get name from node with type " + typeName)
      }

   }

   private getObjectTypeName(node: ts.ObjectTypeDeclaration, considerExplicitNameTags: boolean = true): string {
      if (ts.isInterfaceDeclaration(node)) {
         return this.getName(node.name, considerExplicitNameTags)
      } else if (ts.isClassLike(node)) {
         if (node.name) {
            return this.getName(node.name, considerExplicitNameTags)
         } else {
            throw new Error("Classes without names are not supported")
         }
      } else {
         throw new Error("Unhandled type declaration : " + node.kind)
      }
   }

   private createTypeAlias(node: ts.TypeAliasDeclaration): TypeAlias {
      const aliasedType = this.lookupType(node.type);
      const name = this.getName(node.name);
      const def: TypeAliasDefinition = {
         aliasType: aliasedType,
         annotations: [] // TODO
      };
      return new TypeAlias(name, def);
   }

   private getExplicitName(typeName: ts.EntityName): string | undefined {
      let jsDocs: ts.JSDoc[];
      let container: any = typeName;
      switch (true) {
         case container.hasOwnProperty("jsDoc"):
            jsDocs = container.jsDoc;
            break;
         case container.parent.hasOwnProperty("jsDoc"):
            jsDocs = container.parent.jsDoc;
            break;
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
         const name = dataTypeTag.comment.match(QUOTED_VALUE_REGEX);

         return (name) ? name[0].slice(1, -1) : undefined;
      } else {
         return undefined;
      }
   }

   private hasExplicitName(typeName: ts.EntityName): boolean {
      return this.getExplicitName(typeName) !== undefined
   }
}


function TODO(message: string = "TODO"): Error {
   throw new Error(message)
   return new Error(message)
}

class PrimitiveType implements Type {
   constructor(readonly declaration: string) {

   }

   readonly qualifiedName: string = `lang.taxi.${this.declaration}`
}

class Primitives {
   static BOOLEAN = new PrimitiveType("Boolean");
   static STRING = new PrimitiveType("String");
   static INTEGER = new PrimitiveType("Int");
   static DECIMAL = new PrimitiveType("Decimal");
   static LOCAL_DATE = new PrimitiveType("Date");
   static TIME = new PrimitiveType("Time");
   static INSTANT = new PrimitiveType("Instant");
   static ARRAY = new PrimitiveType("Array");
   static ANY = new PrimitiveType("Any");
   static DOUBLE = new PrimitiveType("Double");
   static VOID = new PrimitiveType("Void");

   static primitives: Map<ts.SyntaxKind, PrimitiveType> = new Map();

   static initialize() {
      this.primitives.set(ts.SyntaxKind.StringKeyword, Primitives.STRING);
      this.primitives.set(ts.SyntaxKind.NumberKeyword, Primitives.INTEGER);
      this.primitives.set(ts.SyntaxKind.BooleanKeyword, Primitives.BOOLEAN);
   }

   static forNodeKind(nodeKind: ts.SyntaxKind): PrimitiveType | undefined {
      return this.primitives.get(nodeKind)
   }

}

Primitives.initialize();

// class Optional<T> {
//    const
// }

class Optional<T> {

   static values<T>(src: Optional<T>[]): T[] {
      return src.filter(o => o.hasValue).map(o => o.value) as T[];
   }

   constructor(readonly value?: T) {
   }

   get hasValue(): boolean {
      return this.value !== undefined
   }

   static empty<T>(): Optional<T> {
      return new Optional<T>()
   }

   static of<T>(value: T): Optional<T> {
      return new Optional<T>(value)
   }
}
