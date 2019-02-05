/**
 * This file is generated - do not edit.
 *
 * Generated at 2019-02-04T13:35:43.993Z
 *
 * To recreate, run the TypescriptEmitter program
 */
import { ObjectType, Type } from "./types";
export interface Named {
    qualifiedName: string;
}
export interface SourceCode {
    content: string;
    origin: string;
}
export interface CompilationUnit {
    ruleContext: any;
    source: SourceCode;
}
export interface Compiled {
}
export interface TypeDefinition {
}
export interface Annotation {
    name: string;
    parameters: {
        [key: string]: any;
    };
}
export interface Annotatable {
    annotations: Annotation[];
}
export interface EnumValue extends Annotatable {
    annotations: Annotation[];
    name: string;
}
export interface EnumDefinition extends Annotatable, TypeDefinition {
    annotations: Annotation[];
    values: EnumValue[];
}
export interface GenericType extends Type {
    parameters: Type[];
}
export interface Constraint {
}
export interface ConstraintTarget {
    constraints: Constraint[];
    description: string;
}
export interface Field extends Annotatable, ConstraintTarget {
    annotations: Annotation[];
    constraints: Constraint[];
    description: string;
    name: string;
    nullable: boolean;
    type: Type;
}
declare type Modifier = "PARAMETER_TYPE";
export interface ObjectTypeDefinition extends TypeDefinition {
    annotations: Annotation[];
    fields: Field[];
    inheritsFrom: ObjectType[];
    modifiers: Modifier[];
}
export interface FieldExtension extends Annotatable {
    annotations: Annotation[];
    name: string;
    refinedType: Type | null;
}
export interface ObjectTypeExtension extends TypeDefinition {
    annotations: Annotation[];
    fieldExtensions: FieldExtension[];
}
export interface TypeAliasDefinition extends TypeDefinition {
    aliasType: Type;
    annotations: Annotation[];
}
export interface TypeAliasExtension extends TypeDefinition {
    annotations: Annotation[];
}
export interface OperationContract extends ConstraintTarget {
    constraints: Constraint[];
    description: string;
    returnType: Type;
    returnTypeConstraints: Constraint[];
}
export interface Parameter extends Annotatable, ConstraintTarget {
    annotations: Annotation[];
    constraints: Constraint[];
    description: string;
    name: string | null;
    type: Type;
}
export interface Operation extends Annotatable, Compiled {
    annotations: Annotation[];
    contract: OperationContract | null;
    name: string;
    parameters: Parameter[];
    returnType: Type;
    scope: string | null;
}
export interface Service extends Annotatable, Named, Compiled {
    annotations: Annotation[];
    operations: Operation[];
    qualifiedName: string;
}
declare type OperationScope = "INTERNAL_AND_EXTERNAL" | "EXTERNAL";
export interface PolicyScope {
    operationScope: OperationScope;
    operationType: string;
}
export interface Condition {
}
declare type InstructionType = "PERMIT" | "FILTER";
export interface Instruction {
    description: string;
    type: InstructionType;
}
export interface PolicyStatement {
    condition: Condition;
    instruction: Instruction;
}
export interface RuleSet {
    scope: PolicyScope;
    statements: PolicyStatement[];
}
export interface Policy extends Annotatable, Named, Compiled {
    annotations: Annotation[];
    qualifiedName: string;
    ruleSets: RuleSet[];
    targetType: Type;
}
export interface TaxiDocument {
    policies: Policy[];
    services: Service[];
    types: Type[];
}
export {};
