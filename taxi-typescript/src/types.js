"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
//
// export interface TypeAlias extends UserType<TypeAliasDefinition, TypeAliasExtension>, Annotatable {
//    aliasType: Type | null;
//    annotations: Annotation[];
//    definition: TypeAliasDefinition | null;
//    extensions: TypeAliasExtension[];
//    qualifiedName: string;
//    referencedTypes: Type[];
// }
class TypeAlias {
    constructor(qualifiedName, definition) {
        this.qualifiedName = qualifiedName;
        this.definition = definition;
        this.annotations = [];
        this.extensions = [];
    }
    get aliasType() {
        return (this.definition) ? this.definition.aliasType : null;
    }
    isDefined() {
        return this.definition !== null;
    }
}
exports.TypeAlias = TypeAlias;
class ObjectType {
    constructor(qualifiedName, definition) {
        this.qualifiedName = qualifiedName;
        this.definition = definition;
        this.extensions = [];
    }
    get fields() {
        return this.definition.fields;
    }
    get inheritsFrom() {
        return this.definition.inheritsFrom;
    }
    field(name) {
        return this.fields.find(f => f.name == name);
    }
    isDefined() {
        return this.definition !== null;
    }
}
exports.ObjectType = ObjectType;
//# sourceMappingURL=types.js.map