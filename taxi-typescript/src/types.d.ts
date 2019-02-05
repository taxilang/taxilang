import { Annotatable, Annotation, Compiled, EnumDefinition, EnumValue, Field, GenericType, Named, ObjectTypeDefinition, ObjectTypeExtension, TaxiDocument, TypeAliasDefinition, TypeAliasExtension, TypeDefinition } from "./schema";
import * as _ from 'lodash';
export declare class TypeAlias implements UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
    readonly qualifiedName: string;
    definition: TypeAliasDefinition | null;
    constructor(qualifiedName: string, definition: TypeAliasDefinition | null);
    kind: TypeKind;
    readonly aliasType: Type | null;
    annotations: Annotation[];
    extensions: TypeAliasExtension[];
    isDefined(): boolean;
}
export declare class ObjectType implements UserType<ObjectTypeDefinition, ObjectTypeExtension> {
    readonly qualifiedName: string;
    definition: ObjectTypeDefinition | null;
    constructor(qualifiedName: string, definition: ObjectTypeDefinition | null);
    kind: TypeKind;
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
export interface NamespacedTaxiDocument extends TaxiDocument {
    namespace: string;
}
export declare class TaxiDocuments {
    static byNamespace<T extends Named>(input: T[]): _.Dictionary<T[]>;
    static toNamespacedDoc(doc: TaxiDocument): NamespacedTaxiDocument[];
}
export declare function escapeReservedWords(input: string): string;
export declare class QualifiedName {
    readonly namespace: string;
    readonly typeName: string;
    constructor(namespace: string, typeName: string);
    readonly escapedTypeName: string;
    readonly fullyQualifiedName: string;
    static forType(type: Named): QualifiedName;
    static from(name: string): QualifiedName;
    qualifyRelativeTo(namespace: string): string;
}
export interface Type extends Named, Compiled {
    kind: TypeKind;
}
export interface EnumType extends UserType<EnumDefinition, EnumDefinition>, Annotatable {
    annotations: Annotation[];
    definition: EnumDefinition | null;
    extensions: EnumDefinition[];
    qualifiedName: string;
    referencedTypes: Type[];
    values: EnumValue[];
}
export declare class ArrayType implements GenericType {
    readonly type: Type;
    constructor(type: Type);
    parameters: Type[];
    qualifiedName: string;
    kind: TypeKind;
}
export declare enum TypeKind {
    ObjectType = 0,
    TypeAliasType = 1,
    EnumType = 2,
    ArrayType = 3,
    PrimitiveType = 4
}
export declare function isObjectType(type: Type): type is ObjectType;
export declare function isTypeAliasType(type: Type): type is TypeAlias;
export declare function isArrayType(type: Type): type is ArrayType;
