import * as ts from 'typescript';
import {Field, TypeAliasDefinition} from "./schema";
import {ObjectType, Type, TypeAlias, TypeKind, UserType} from "./types";
import * as _ from 'lodash';
import {TypeHelper} from "./typeHelper";

// Regex for matching values inside a quotation.
// https://stackoverflow.com/a/171499/59015
// I hate regex.
const QUOTED_VALUE_REGEX = new RegExp(/(["'])(\\?.)*?\1/g);


export interface TypeMapperFactory {
   build(typeHelper: TypeHelper): TypeMapper
}

export interface TypeMapper {

   // generate(node: ts.ObjectTypeDeclaration): Type

   // buildTypes(): Type[]

   readonly types: Type[];

   getTypeOrDefault(node: ts.Node | undefined, defaultType: Type): Type;
}

export class DefaultTypeMapperFactory implements TypeMapperFactory {
   build(typeHelper: TypeHelper): TypeMapper {
      return new DefaultTypeGenerator(typeHelper)
   }

}

export class DefaultTypeGenerator implements TypeMapper {
   constructor(readonly typeHelper: TypeHelper) {
      this.build()
   }

   private constructedTypes: Map<string, UserType<any, any>> = new Map();

   private build() {
      this.typeHelper.objectTypeDeclarations
         .filter(typeNode => {
            return this.isDeclaredDataType(typeNode);
         })
         .map(type => this.generate(type));
   }


   getTypeOrDefault(node: ts.Node, defaultType: Type): Type {
      if (!node) {
         return defaultType;
      }
      if (ts.isTypeReferenceNode(node)) {
         return this.getOrBuildType(this.typeHelper.getNameFromIdentifier(node.typeName))
      } else {
         throw TODO("Not sure how to lookup type")
      }
   }

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
      let typescriptDeclaredName = this.typeHelper.getObjectTypeName(node, false);
      let typeNameInTaxi = this.typeHelper.getObjectTypeName(node, true);
      let type = new ObjectType(typeNameInTaxi, null);

      // Store the type now, undefined, so that other types may reference it (or it may reference itself)
      // Store the type using it's name as it appears in ts, not as it will be generated in taxi
      this.constructedTypes.set(typescriptDeclaredName, type);

      let fields: Field[] = Optional.values((node.members as ts.NodeArray<any>)
         .map((member: any) => this.generateField(member)));

      let heritageClauses: ts.HeritageClause[] = (<any>node).heritageClauses || [];
      let inheritedTypes = _.flatMap(heritageClauses, clause => clause.types.map(inheritedType => {
         if (ts.isIdentifier(inheritedType.expression)) {
            return this.getOrBuildType(inheritedType.expression.escapedText.toString()) as ObjectType
         } else {
            throw new Error("Not sure what to do here.")
         }

         // this.getOrBuildType(inheritedType.expression.escapedText) as ObjectType
      }));
      type.definition = {
         annotations: [], // TODO
         fields: fields,
         inheritsFrom: inheritedTypes,
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
         let typeName = this.typeHelper.getNameFromIdentifier(type.typeName);
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
         let typeDeclarationNode = this.typeHelper.findType(typescriptTypeName);
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

   private createTypeAlias(node: ts.TypeAliasDeclaration): TypeAlias {
      const aliasedType = this.lookupType(node.type);
      const name = this.typeHelper.getName(node);
      const def: TypeAliasDefinition = {
         aliasType: aliasedType,
         annotations: [] // TODO
      };
      return new TypeAlias(name, def);
   }


   private isDeclaredDataType(typeNode: ts.ObjectTypeDeclaration): boolean {
      if (ts.isInterfaceDeclaration(typeNode)) {
         let dataTypeTags = this.typeHelper.getJsDocTags(typeNode, "DataType", false);
         return dataTypeTags.length > 0;
      }
      return false;
   }

}


function TODO(message: string = "TODO"): Error {
   throw new Error(message)
   return new Error(message)
}

class PrimitiveType implements Type {
   kind: TypeKind = TypeKind.PrimitiveType;

   constructor(readonly declaration: string) {

   }

   readonly qualifiedName: string = `lang.taxi.${this.declaration}`
}

export class Primitives {
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
