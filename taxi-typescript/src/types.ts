import {
   Annotatable,
   Annotation,
   Field,
   ObjectTypeDefinition,
   ObjectTypeExtension,
   Type,
   TypeAliasDefinition,
   TypeAliasExtension,
   TypeDefinition
} from "./schema";
//
// export interface TypeAlias extends UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
//    aliasType: Type | null;
//    annotations: Annotation[];
//    definition: TypeAliasDefinition | null;
//    extensions: TypeAliasExtension[];
//    qualifiedName: string;
//    referencedTypes: Type[];
// }

export class TypeAlias implements UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
   constructor(readonly qualifiedName: string, public  definition: TypeAliasDefinition | null) {

   }

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
