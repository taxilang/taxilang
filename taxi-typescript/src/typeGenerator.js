"use strict";
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const ts = __importStar(require("typescript"));
const types_1 = require("./types");
const _ = __importStar(require("lodash"));
// Regex for matching values inside a quotation.
// https://stackoverflow.com/a/171499/59015
// I hate regex.
const QUOTED_VALUE_REGEX = new RegExp(/(["'])(\\?.)*?\1/g);
class DefaultTypeMapperFactory {
    build(typeHelper) {
        return new DefaultTypeGenerator(typeHelper);
    }
}
exports.DefaultTypeMapperFactory = DefaultTypeMapperFactory;
class DefaultTypeGenerator {
    constructor(typeHelper) {
        this.typeHelper = typeHelper;
        this.constructedTypes = new Map();
        this.build();
    }
    build() {
        this.typeHelper.objectTypeDeclarations
            .filter(typeNode => {
            return this.isDeclaredDataType(typeNode);
        })
            .map(type => this.generate(type));
    }
    getTypeOrDefault(node, defaultType) {
        if (!node) {
            return defaultType;
        }
        if (ts.isTypeReferenceNode(node)) {
            return this.getOrBuildType(this.typeHelper.getNameFromIdentifier(node.typeName));
        }
        else {
            throw TODO("Not sure how to lookup type");
        }
    }
    get types() {
        return Array.from(this.constructedTypes.values());
    }
    generate(node) {
        // TODO : For now, assume all user types
        if (ts.isTypeAliasDeclaration(node)) {
            const typeAlias = this.createTypeAlias(node);
            this.constructedTypes.set(typeAlias.qualifiedName, typeAlias);
            return typeAlias;
        }
        let typescriptDeclaredName = this.typeHelper.getObjectTypeName(node, false);
        let typeNameInTaxi = this.typeHelper.getObjectTypeName(node, true);
        let type = new types_1.ObjectType(typeNameInTaxi, null);
        // Store the type now, undefined, so that other types may reference it (or it may reference itself)
        // Store the type using it's name as it appears in ts, not as it will be generated in taxi
        this.constructedTypes.set(typescriptDeclaredName, type);
        let fields = Optional.values(node.members
            .map((member) => this.generateField(member)));
        let heritageClauses = node.heritageClauses || [];
        let inheritedTypes = _.flatMap(heritageClauses, clause => clause.types.map(inheritedType => {
            if (ts.isIdentifier(inheritedType.expression)) {
                return this.getOrBuildType(inheritedType.expression.escapedText.toString());
            }
            else {
                throw new Error("Not sure what to do here.");
            }
            // this.getOrBuildType(inheritedType.expression.escapedText) as ObjectType
        }));
        type.definition = {
            annotations: [],
            fields: fields,
            inheritsFrom: inheritedTypes,
            modifiers: [] // TODO
        };
        return type;
    }
    generateField(member) {
        switch (member.kind) {
            case ts.SyntaxKind.PropertySignature:
                return Optional.of(this.generateFieldFromProperty(member));
            case ts.SyntaxKind.SetAccessor:
            case ts.SyntaxKind.GetAccessor:
                return Optional.empty();
            // ignore, I think these are alreaddy handled?
            default:
                throw new Error("Unhandled");
        }
    }
    generateFieldFromProperty(member) {
        const type = (member.type) ? this.lookupType(member.type) : Primitives.ANY;
        const name = this.getPropertyName(member.name);
        return {
            name: name,
            type: type,
            annotations: [],
            constraints: [],
            description: "unused",
            nullable: false // TODO
        };
        //
        throw "";
    }
    lookupType(type) {
        if (Primitives.forNodeKind(type.kind)) {
            return Primitives.forNodeKind(type.kind);
        }
        else if (ts.isTypeReferenceNode(type)) {
            let typeName = this.typeHelper.getNameFromIdentifier(type.typeName);
            return this.getOrBuildType(typeName);
        }
        else {
            console.log("Unhanled type.kind:  " + type.kind);
            return Primitives.ANY; // TODO
        }
    }
    getOrBuildType(typescriptTypeName) {
        if (this.constructedTypes.has(typescriptTypeName)) {
            return this.constructedTypes.get(typescriptTypeName);
        }
        else {
            let typeDeclarationNode = this.typeHelper.findType(typescriptTypeName);
            if (!typeDeclarationNode) {
                throw new Error(`No definition for type ${typescriptTypeName} found, cannot construct typedef.`);
            }
            return this.generate(typeDeclarationNode);
        }
    }
    getPropertyName(name) {
        if (ts.isIdentifier(name)) {
            return name.escapedText.toString();
        }
        else if (ts.isStringLiteral(name) || ts.isNumericLiteral(name)) {
            return name.text;
        }
        else {
            throw Error("Unadanled name type");
        }
    }
    createTypeAlias(node) {
        const aliasedType = this.lookupType(node.type);
        const name = this.typeHelper.getName(node);
        const def = {
            aliasType: aliasedType,
            annotations: [] // TODO
        };
        return new types_1.TypeAlias(name, def);
    }
    isDeclaredDataType(typeNode) {
        if (ts.isInterfaceDeclaration(typeNode)) {
            let dataTypeTags = this.typeHelper.getJsDocTags(typeNode, "DataType");
            return dataTypeTags.length > 0;
        }
        return false;
    }
}
exports.DefaultTypeGenerator = DefaultTypeGenerator;
function TODO(message = "TODO") {
    throw new Error(message);
    return new Error(message);
}
class PrimitiveType {
    constructor(declaration) {
        this.declaration = declaration;
        this.kind = types_1.TypeKind.PrimitiveType;
        this.qualifiedName = `lang.taxi.${this.declaration}`;
    }
}
class Primitives {
    static initialize() {
        this.primitives.set(ts.SyntaxKind.StringKeyword, Primitives.STRING);
        this.primitives.set(ts.SyntaxKind.NumberKeyword, Primitives.INTEGER);
        this.primitives.set(ts.SyntaxKind.BooleanKeyword, Primitives.BOOLEAN);
    }
    static forNodeKind(nodeKind) {
        return this.primitives.get(nodeKind);
    }
}
Primitives.BOOLEAN = new PrimitiveType("Boolean");
Primitives.STRING = new PrimitiveType("String");
Primitives.INTEGER = new PrimitiveType("Int");
Primitives.DECIMAL = new PrimitiveType("Decimal");
Primitives.LOCAL_DATE = new PrimitiveType("Date");
Primitives.TIME = new PrimitiveType("Time");
Primitives.INSTANT = new PrimitiveType("Instant");
Primitives.ARRAY = new PrimitiveType("Array");
Primitives.ANY = new PrimitiveType("Any");
Primitives.DOUBLE = new PrimitiveType("Double");
Primitives.VOID = new PrimitiveType("Void");
Primitives.primitives = new Map();
exports.Primitives = Primitives;
Primitives.initialize();
// class Optional<T> {
//    const
// }
class Optional {
    constructor(value) {
        this.value = value;
    }
    static values(src) {
        return src.filter(o => o.hasValue).map(o => o.value);
    }
    get hasValue() {
        return this.value !== undefined;
    }
    static empty() {
        return new Optional();
    }
    static of(value) {
        return new Optional(value);
    }
}
//# sourceMappingURL=typeGenerator.js.map