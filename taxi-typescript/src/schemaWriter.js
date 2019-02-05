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
const types_1 = require("./types");
const typeGenerator_1 = require("./typeGenerator");
class SchemaWriter {
    generateSchemas(docs) {
        return _.flatMap(docs, d => this.generateSchema(d));
    }
    generateSchema(doc) {
        let generated = types_1.TaxiDocuments.toNamespacedDoc(doc).map(namespacedDoc => {
            let types = namespacedDoc.types.map(type => this.generateTypeDeclaration(type, namespacedDoc.namespace));
            let typesTaxiString = types.join("\n\n").trim();
            let services = namespacedDoc.services.map(service => this.generateServiceDeclaration(service, namespacedDoc.namespace));
            let serviceString = services.join("\n\n").trim();
            let declarations = `
${typesTaxiString}
${serviceString}
         `;
            if (namespacedDoc.namespace) {
                return `namespace ${namespacedDoc.namespace} {
   ${declarations}
   }
            `;
            }
            else {
                return declarations;
            }
        });
        return generated;
    }
    generateTypeDeclaration(type, namespace) {
        if (types_1.isObjectType(type)) {
            return this.generateObjectTypeDeclaration(type, namespace);
        }
        else if (types_1.isTypeAliasType(type)) {
            return this.generateTypeAliasDeclaration(type, namespace);
        }
        else {
            throw new Error("Unhandled type for type : " + type.qualifiedName);
        }
    }
    generateObjectTypeDeclaration(type, namespace) {
        let fieldDeclarations = type.fields.map(field => this.generateFieldDeclaration(field, namespace)).join("\n");
        let modifiers = type.definition.modifiers.map(m => m.toString()).join(" ");
        let inheritanceString = this.getInheritenceString(type);
        return `${modifiers} type ${types_1.QualifiedName.forType(type).escapedTypeName}${inheritanceString} {
   ${fieldDeclarations}
      }`;
    }
    generateFieldDeclaration(field, namespace) {
        let fieldType = this.typeAsTaxi(field.type, namespace);
        // TODO : Constraints
        return `${types_1.escapeReservedWords(field.name)} : ${fieldType}`.trim();
    }
    typeAsTaxi(type, namespace) {
        if (types_1.isArrayType(type)) {
            return this.typeAsTaxi(type.type, namespace) + "[]";
        }
        else {
            return types_1.QualifiedName.from(type.qualifiedName).qualifyRelativeTo(namespace);
        }
    }
    getInheritenceString(type) {
        if (!type.inheritsFrom || type.inheritsFrom.length === 0) {
            return "";
        }
        else {
            return ` inherits ${type.inheritsFrom.map(t => t.qualifiedName).join(" ")}`;
        }
    }
    generateTypeAliasDeclaration(type, namespace) {
        let aliasTypeString = this.typeAsTaxi(type.aliasType, namespace);
        return `type alias ${types_1.QualifiedName.forType(type).escapedTypeName} as ${aliasTypeString}`;
    }
    generateServiceDeclaration(service, namespace) {
        let operations = service.operations.map(operation => this.generateOperationDeclaration(operation, namespace));
        return `
${this.generateAnnotations(service)}
service ${types_1.QualifiedName.forType(service).qualifyRelativeTo(namespace)} {
${operations}
}`.trim();
    }
    generateOperationDeclaration(operation, namespace) {
        let paramsString = operation.parameters.map(param => {
            let constraintString = ""; // TODO
            let annotations = ""; // TODO
            let paramName = (param.name) ? `${param.name} : ` : "";
            let paramDeclaration = this.typeAsTaxi(param.type, namespace);
            return annotations + paramName + paramDeclaration + constraintString;
        }).join(", ");
        let returnType;
        if (operation.returnType === typeGenerator_1.Primitives.VOID) {
            returnType = "";
        }
        else {
            let returnTypeName = this.typeAsTaxi(operation.returnType, namespace);
            let returnContract = ""; // TODO
            returnType = (" : " + returnTypeName + returnContract);
        }
        let scope = (operation.scope) ? operation.scope + " " : "";
        let annotations = this.generateAnnotations(operation);
        return `${annotations}
   ${scope}operation ${operation.name}( ${paramsString} )${returnType}`.trim();
    }
    quoteIfNeeded(param) {
        if (typeof param === "boolean") {
            return param.toString();
        }
        else if (this.isNumber(param)) {
            return param.toString();
        }
        else {
            return `"${param.toString()}"`;
        }
    }
    isNumber(value) {
        return !isNaN(Number(value.toString()));
    }
    generateAnnotations(element) {
        return element.annotations.map(annotation => {
            let paramKeys = Object.keys(annotation.parameters);
            if (paramKeys.length === 0) {
                return "@" + annotation.name;
            }
            let annotationParams = paramKeys.map(key => {
                `${key} = ${this.quoteIfNeeded(annotation.parameters[key])}`;
            });
            return `@${annotation.name}(${annotationParams})`;
        }).join("\n");
    }
}
exports.SchemaWriter = SchemaWriter;
//# sourceMappingURL=schemaWriter.js.map