"use strict";
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const typeGenerator_1 = require("./typeGenerator");
const ts = __importStar(require("typescript"));
const utils_1 = require("./utils");
class DefaultServiceMapper {
    constructor(typeHelper, typeMapper) {
        this.typeHelper = typeHelper;
        this.typeMapper = typeMapper;
        this.services = this.build();
    }
    build() {
        const serviceNodes = this.typeHelper.getTypesWithJsDocTag("Service");
        return serviceNodes.map(node => this.generateService(node));
        // throw new Error("Not implemented")
    }
    generateService(node) {
        let operationMembers = this.typeHelper.getMembersWithJsDocTag(node, "Operation");
        let operations = operationMembers.map(member => this.generateOperation(member));
        let serviceName = this.typeHelper.getObjectTypeName(node);
        return {
            qualifiedName: serviceName,
            operations: operations,
            annotations: []
        };
    }
    generateOperation(member) {
        if (ts.isMethodSignature(member)) {
            let name = this.typeHelper.getNameFromIdentifier(member.name);
            let returnType = this.typeMapper.getTypeOrDefault(member.type, typeGenerator_1.Primitives.VOID);
            let parameters = member.parameters.map(param => {
                let paramType = this.typeMapper.getTypeOrDefault(param.type, typeGenerator_1.Primitives.ANY);
                let paramName;
                if (ts.isIdentifier(param.name)) {
                    paramName = this.typeHelper.getNameFromIdentifier(param.name);
                }
                else {
                    paramName = null;
                }
                return {
                    name: paramName,
                    type: paramType,
                    annotations: [],
                    constraints: [],
                    description: ""
                };
            });
            return {
                name: name,
                annotations: [],
                contract: null,
                parameters: parameters,
                returnType: returnType,
                scope: null // TODO
            };
        }
        else {
            return utils_1.TODO("Unhandled method type, cannot generate operation");
        }
        let name = member.name;
        return utils_1.TODO("error");
    }
}
exports.DefaultServiceMapper = DefaultServiceMapper;
class DefaultServiceGeneratorFactory {
    build(typeHelper, typeMapper) {
        return new DefaultServiceMapper(typeHelper, typeMapper);
    }
}
exports.DefaultServiceGeneratorFactory = DefaultServiceGeneratorFactory;
//# sourceMappingURL=serviceGenerator.js.map