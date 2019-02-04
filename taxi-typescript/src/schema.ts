/**
 * This file is generated - do not edit.
 *
 * Generated at 2019-02-01T16:40:19.322Z
 *
 * To recreate, run the TypescriptEmitter program
 */

 import { UserType, ObjectType} from "./types";
export interface Named {
    qualifiedName: string;
}

export interface Tree {
}

export interface SyntaxTree extends Tree {
}

export interface ParseTree extends SyntaxTree {
}

export interface RuleNode extends ParseTree {
}

export interface RuleContext extends RuleNode {
    parent: RuleContext;
    invokingState: number;
}

export interface Token {
}

export interface Throwable {
    cause: Throwable | null;
    message: string | null;
}

export interface Exception extends Throwable {
}

export interface RuntimeException extends Exception {
}

export interface Transition {
    target: ATNState;
}

export interface IntSet {
}

export interface Interval {
    a: number;
    b: number;
}

export interface IntervalSet extends IntSet {
    intervals: Interval[];
    readonly: boolean;
}

export interface ATNState {
    atn: ATN;
    stateNumber: number;
    ruleIndex: number;
    epsilonOnlyTransitions: boolean;
    transitions: Transition[];
    nextTokenWithinRule: IntervalSet;
}

export interface DecisionState extends ATNState {
    decision: number;
    nonGreedy: boolean;
}

export interface RuleStopState extends ATNState {
}

export interface RuleStartState extends ATNState {
    stopState: RuleStopState;
    isLeftRecursiveRule: boolean;
}

export interface TokensStartState extends DecisionState {
}

type ATNType = "LEXER" | "PARSER";

export interface LexerAction {
}

export interface ATN {
    states: ATNState[];
    decisionToState: DecisionState[];
    ruleToStartState: RuleStartState[];
    ruleToStopState: RuleStopState[];
    modeNameToStartState: { [key: string]: TokensStartState };
    grammarType: ATNType;
    maxTokenType: number;
    ruleToTokenType: number[];
    lexerActions: LexerAction[];
    modeToStartState: TokensStartState[];
}

export interface PredictionContextCache {
}

export interface ATNSimulator {
    atn: ATN;
    sharedContextCache: PredictionContextCache;
}

export interface Recognizer<Symbol, ATNInterpreter extends ATNSimulator> {
}

export interface RecognitionException extends RuntimeException {
    recognizer: Recognizer<any, any>;
    ctx: RuleContext;
    offendingToken: Token;
    offendingState: number;
}

export interface ParserRuleContext extends RuleContext {
    children: ParseTree[];
    start: Token;
    stop: Token;
    exception: RecognitionException;
}

export interface SourceCode {
    content: string;
    origin: string;
}

export interface CompilationUnit {
    ruleContext: ParserRuleContext | null;
    source: SourceCode;
}

export interface Compiled {
    
}

export interface Type extends Named, Compiled {
}

export interface TypeDefinition {
    
}

export interface Annotation {
    name: string;
    parameters: { [key: string]: any };
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

export interface EnumType extends UserType<EnumDefinition,EnumDefinition>, Annotatable {
    annotations: Annotation[];
    definition: EnumDefinition | null;
    extensions: EnumDefinition[];
    qualifiedName: string;
    referencedTypes: Type[];
    values: EnumValue[];
}

export interface GenericType extends Type {
    parameters: Type[];
}

export interface ArrayType extends GenericType {
    
    parameters: Type[];
    qualifiedName: string;
    
    type: Type;
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

type Modifier = "PARAMETER_TYPE";

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

type PrimitiveType = "BOOLEAN" | "STRING" | "INTEGER" | "DECIMAL" | "LOCAL_DATE" | "TIME" | "DATE_TIME" | "INSTANT" | "ARRAY" | "ANY" | "DOUBLE" | "VOID";

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

type OperationScope = "INTERNAL_AND_EXTERNAL" | "EXTERNAL";

export interface PolicyScope {
    operationScope: OperationScope;
    operationType: string;
}

export interface Condition {
}

type InstructionType = "PERMIT" | "FILTER";

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
