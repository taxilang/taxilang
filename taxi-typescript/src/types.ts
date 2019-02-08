import {
   Annotatable,
   Annotation,
   Compiled,
   EnumDefinition,
   EnumValue,
   Field,
   GenericType,
   Named,
   ObjectTypeDefinition,
   ObjectTypeExtension,
   TaxiDocument,
   TypeAliasDefinition,
   TypeAliasExtension,
   TypeDefinition
} from "./schema";
import * as _ from 'lodash'

export class TypeAlias implements UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
   constructor(readonly qualifiedName: string, public  definition: TypeAliasDefinition | null) {

   }

   kind: TypeKind = TypeKind.TypeAliasType;

   get aliasType(): Type | null {
      return (this.definition) ? this.definition.aliasType : null;
   }

   annotations: Annotation[] = [];
   extensions: TypeAliasExtension[] = [];

   isDefined(): boolean {
      return this.definition !== null;
   }
}

export class ObjectType implements UserType<ObjectTypeDefinition, ObjectTypeExtension> {
   constructor(readonly qualifiedName: string, public  definition: ObjectTypeDefinition | null) {

   }

   kind: TypeKind = TypeKind.ObjectType;

   get fields(): Field[] {
      return this.definition!.fields;
   }

   get inheritsFrom(): ObjectType[] {
      return this.definition!.inheritsFrom;
   }

   field(name: string): Field {
      return this.fields.find(f => f.name == name)!;
   }


   extensions: ObjectTypeExtension[] = [];

   isDefined(): boolean {
      return this.definition !== null;
   }

}

export interface UserType<TDef extends TypeDefinition, TExt extends TypeDefinition> extends Type {

   definition: TDef | null;
   extensions: TExt[];

   isDefined(): boolean;
}

export interface NamespacedTaxiDocument extends TaxiDocument {
   namespace: string
}

export class TaxiDocuments {
   static byNamespace<T extends Named>(input: T[]): _.Dictionary<T[]> {
      return _.groupBy(input, (named: Named) => QualifiedName.from(named.qualifiedName).namespace);
   }

   static toNamespacedDoc(doc: TaxiDocument): NamespacedTaxiDocument[] {

      let typesByNamespace = this.byNamespace(doc.types);
      let servicesByNamespace = this.byNamespace(doc.services);
      let policiesByNamespace = this.byNamespace(doc.policies);

      let namespaces = _.uniq(Object.keys(typesByNamespace).concat(Object.keys(servicesByNamespace)));
      return namespaces.map(namespace => {
         return <NamespacedTaxiDocument>{
            namespace: namespace,
            types: typesByNamespace[namespace] || [],
            services: servicesByNamespace[namespace] || [],
            policies: policiesByNamespace[namespace] || []
         }
      })
   }
}

const reservedWords = ["type", "service", "alias"];

export function escapeReservedWords(input: string): string {
   if (reservedWords.indexOf(input) != -1) {
      return "`" + input + "`"
   } else {
      return input
   }
}

export class QualifiedName {

   constructor(readonly namespace: string, readonly typeName: string) {
   }

   get escapedTypeName(): string {
      return escapeReservedWords(this.typeName);
   }

   get fullyQualifiedName(): string {
      return (this.namespace) ? `${this.namespace}.${this.typeName}` : this.typeName
   }

   static forType(type: Named): QualifiedName {
      return this.from(type.qualifiedName);
   }

   static from(name: string): QualifiedName {
      let parts = name.split(".");
      let typeName = parts[parts.length - 1];
      let namespace = parts.slice(0, parts.length - 1);
      return new QualifiedName(namespace.join("."), typeName);
   }

   qualifyRelativeTo(namespace: string): string {
      return (namespace == this.namespace) ? this.typeName : this.fullyQualifiedName
   }
}

export interface Type extends Named, Compiled {
   kind: TypeKind
}

export interface EnumType extends UserType<EnumDefinition, EnumDefinition>, Annotatable {
   annotations: Annotation[];
   definition: EnumDefinition | null;
   extensions: EnumDefinition[];
   qualifiedName: string;
   referencedTypes: Type[];
   values: EnumValue[];
}


export class ArrayType implements GenericType {
   constructor(readonly type: Type) {
      this.qualifiedName = `lang.taxi.Array<${this.type.qualifiedName}>`;
      this.parameters = [type];
   }

   parameters: Type[];
   qualifiedName: string;

   kind: TypeKind = TypeKind.ArrayType
}

export enum TypeKind {
   ObjectType,
   TypeAliasType,
   EnumType,
   ArrayType,
   PrimitiveType
}

export function isObjectType(type: Type): type is ObjectType {
   return type.kind == TypeKind.ObjectType
}

export function isTypeAliasType(type: Type): type is TypeAlias {
   return type.kind == TypeKind.TypeAliasType
}

export function isArrayType(type: Type): type is ArrayType {
   return type.kind == TypeKind.ArrayType
}
