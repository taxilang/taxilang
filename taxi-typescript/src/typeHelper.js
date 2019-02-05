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
const _ = __importStar(require("lodash"));
class TypeHelper {
    constructor(nodes) {
        this.nodes = nodes;
    }
    hasJsDocTag(tagName) {
        return (node) => {
            return this.getJsDocTags(node, tagName).length > 0;
        };
    }
    getTypesWithJsDocTag(tagName) {
        return this.getTypeDeclarations(this.hasJsDocTag(tagName));
    }
    getTypeDeclarations(filter) {
        return this.nodes
            .filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
            .filter(filter)
            .map(n => n);
    }
    get objectTypeDeclarations() {
        return this.nodes.filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
            .map(n => n);
        // .forEach(type => this.generate(<ts.ObjectTypeDeclaration>type));
    }
    findType(typescriptTypeName) {
        let typeDeclarationNode = this.nodes.find(node => {
            // Don't consider the @DataType( ... ) name when looking in source, as we're looking for the token, not the compiler type
            if (ts.isInterfaceDeclaration(node) && this.getObjectTypeName(node, false) == typescriptTypeName)
                return true;
            if (ts.isTypeAliasDeclaration(node) && this.getName(node, false) == typescriptTypeName)
                return true;
            return false;
        });
        if (!typeDeclarationNode) {
            throw new Error(`No type with name ${typescriptTypeName} is defined`);
        }
        return typeDeclarationNode;
    }
    getNameFromIdentifier(identifier) {
        if (ts.isIdentifier(identifier)) {
            return identifier.escapedText.toString();
        }
        else if (ts.isQualifiedName(identifier)) {
            return identifier.right.escapedText.toString();
        }
        throw new Error("Unhandled name from identifier case");
    }
    getName(typeWithName, considerExplicitNameTags = true) {
        if (considerExplicitNameTags && this.hasExplicitName(typeWithName)) {
            return this.getExplicitName(typeWithName);
        }
        else if (typeWithName.name && ts.isIdentifier(typeWithName.name)) {
            return typeWithName.name.escapedText.toString();
        }
        else if (typeWithName.name && ts.isQualifiedName(typeWithName.name)) {
            return typeWithName.name.right.escapedText.toString();
        }
        else {
            throw new Error("Unable to get name from node with type " + typeWithName);
        }
    }
    getObjectTypeName(node, considerExplicitNameTags = true) {
        if (ts.isInterfaceDeclaration(node)) {
            return this.getName(node, considerExplicitNameTags);
        }
        else if (ts.isClassLike(node)) {
            if (node.name) {
                return this.getName(node, considerExplicitNameTags);
            }
            else {
                throw new Error("Classes without names are not supported");
            }
        }
        else {
            throw new Error("Unhandled type declaration : " + node.kind);
        }
    }
    getExplicitName(typeName) {
        let jsDocs;
        let container = typeName;
        switch (true) {
            case container.hasOwnProperty("jsDoc"):
                jsDocs = container.jsDoc;
                break;
            // case container.parent && container.parent.hasOwnProperty("jsDoc"):
            //    jsDocs = container.parent.jsDoc;
            //    break;
            default:
                return undefined;
        }
        let dataTypeTags = jsDocs
            .filter(doc => doc.tags)
            .map(doc => {
            return doc.tags.find(tag => tag.tagName.escapedText === "DataType");
        });
        if (!dataTypeTags)
            return undefined;
        let dataTypeTag = dataTypeTags[0];
        if (dataTypeTag && dataTypeTag.comment) {
            // const name = dataTypeTag.comment.match(QUOTED_VALUE_REGEX);
            // Only take the text up until the first non whitespace char (or newline)
            return dataTypeTag.comment.trim().split(/\s+/)[0];
        }
        else {
            return undefined;
        }
    }
    hasExplicitName(typeName) {
        return this.getExplicitName(typeName) !== undefined;
    }
    getJsDocTags(node, tagName) {
        if (!this.isJsDocContainer(node)) {
            return [];
        }
        let docs = node.jsDoc;
        let docsWithTags = docs.filter(doc => doc.tags);
        let tags = _.flatMap(docsWithTags, jsDoc => {
            return jsDoc.tags || [];
        })
            .filter(tag => tag.tagName.escapedText === tagName);
        return tags;
    }
    isJsDocContainer(node) {
        return node.hasOwnProperty("jsDoc");
    }
    getMembersWithJsDocTag(node, tagName) {
        if (ts.isClassLike(node)) {
            return node.members.filter(this.hasJsDocTag(tagName));
        }
        else if (ts.isInterfaceDeclaration(node)) {
            return node.members.filter(this.hasJsDocTag(tagName));
        }
        else if (ts.isTypeLiteralNode(node)) {
            return node.members.filter(this.hasJsDocTag(tagName));
        }
        else {
            throw new Error("Unhandled members type");
        }
    }
}
exports.TypeHelper = TypeHelper;
//# sourceMappingURL=typeHelper.js.map