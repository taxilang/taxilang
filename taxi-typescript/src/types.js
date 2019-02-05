"use strict";
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const _ = __importStar(require("lodash"));
class TypeAlias {
    constructor(qualifiedName, definition) {
        this.qualifiedName = qualifiedName;
        this.definition = definition;
        this.kind = TypeKind.TypeAliasType;
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
        this.kind = TypeKind.ObjectType;
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
class TaxiDocuments {
    static byNamespace(input) {
        return _.groupBy(input, (named) => QualifiedName.from(named.qualifiedName).namespace);
    }
    static toNamespacedDoc(doc) {
        let typesByNamespace = this.byNamespace(doc.types);
        let servicesByNamespace = this.byNamespace(doc.services);
        let policiesByNamespace = this.byNamespace(doc.policies);
        let namespaces = _.uniq(Object.keys(typesByNamespace).concat(Object.keys(servicesByNamespace)));
        return namespaces.map(namespace => {
            return {
                namespace: namespace,
                types: typesByNamespace[namespace] || [],
                services: servicesByNamespace[namespace] || [],
                policies: policiesByNamespace[namespace] || []
            };
        });
    }
}
exports.TaxiDocuments = TaxiDocuments;
const reservedWords = ["type", "service", "alias"];
function escapeReservedWords(input) {
    if (reservedWords.indexOf(input) != -1) {
        return "`" + input + "`";
    }
    else {
        return input;
    }
}
exports.escapeReservedWords = escapeReservedWords;
class QualifiedName {
    constructor(namespace, typeName) {
        this.namespace = namespace;
        this.typeName = typeName;
    }
    get escapedTypeName() {
        return escapeReservedWords(this.typeName);
    }
    get fullyQualifiedName() {
        return `${this.namespace}.${this.typeName}`;
    }
    static forType(type) {
        return this.from(type.qualifiedName);
    }
    static from(name) {
        let parts = name.split(".");
        let typeName = parts[parts.length - 1];
        let namespace = parts.slice(0, parts.length - 1);
        return new QualifiedName(namespace.join("."), typeName);
    }
    qualifyRelativeTo(namespace) {
        return (namespace == this.namespace) ? this.typeName : this.fullyQualifiedName;
    }
}
exports.QualifiedName = QualifiedName;
class ArrayType {
    constructor(type) {
        this.type = type;
        this.kind = TypeKind.ArrayType;
        this.qualifiedName = `lang.taxi.Array<${this.type.qualifiedName}>`;
        this.parameters = [type];
    }
}
exports.ArrayType = ArrayType;
var TypeKind;
(function (TypeKind) {
    TypeKind[TypeKind["ObjectType"] = 0] = "ObjectType";
    TypeKind[TypeKind["TypeAliasType"] = 1] = "TypeAliasType";
    TypeKind[TypeKind["EnumType"] = 2] = "EnumType";
    TypeKind[TypeKind["ArrayType"] = 3] = "ArrayType";
    TypeKind[TypeKind["PrimitiveType"] = 4] = "PrimitiveType";
})(TypeKind = exports.TypeKind || (exports.TypeKind = {}));
function isObjectType(type) {
    return type.kind == TypeKind.ObjectType;
}
exports.isObjectType = isObjectType;
function isTypeAliasType(type) {
    return type.kind == TypeKind.TypeAliasType;
}
exports.isTypeAliasType = isTypeAliasType;
function isArrayType(type) {
    return type.kind == TypeKind.ArrayType;
}
exports.isArrayType = isArrayType;
//# sourceMappingURL=types.js.map