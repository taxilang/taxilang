import { Annotatable, Annotation, Field, ObjectTypeDefinition, ObjectTypeExtension, Type, TypeAliasDefinition, TypeAliasExtension, TypeDefinition } from "./schema";
export declare class TypeAlias implements UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
    readonly qualifiedName: string;
    definition: TypeAliasDefinition | null;
    constructor(qualifiedName: string, definition: TypeAliasDefinition | null);
    readonly aliasType: Type | null;
    annotations: Annotation[];
    extensions: TypeAliasExtension[];
    isDefined(): boolean;
}
export declare class ObjectType implements UserType<ObjectTypeDefinition, ObjectTypeExtension> {
    readonly qualifiedName: string;
    definition: ObjectTypeDefinition | null;
    constructor(qualifiedName: string, definition: ObjectTypeDefinition | null);
    readonly fields: Field[];
    readonly inheritsFrom: ObjectType[];
    field(name: string): Field;
    extensions: ObjectTypeExtension[];
    isDefined(): boolean;
}
export interface UserType<TDef extends TypeDefinition, TExt extends TypeDefinition> extends Type {
    definition: TDef | null;
    extensions: TExt[];
    isDefined(): boolean;
}
