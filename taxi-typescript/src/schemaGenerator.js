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
const typeGenerator_1 = require("./typeGenerator");
class SchemaGenerator {
    constructor(entryFiles, compilerHost) {
        this.nodes = [];
        this.program = ts.createProgram(entryFiles, {}, compilerHost);
        this.typeChecker = this.program.getTypeChecker();
    }
    generate() {
        this.program.getSourceFiles().forEach(sourceFile => {
            if (!sourceFile.isDeclarationFile) {
                ts.forEachChild(sourceFile, node => {
                    this.nodes.push(node);
                });
            }
        });
        const typeMapper = new typeGenerator_1.TypeGenerator(this.nodes);
        const types = this.nodes.filter(node => node.kind == ts.SyntaxKind.InterfaceDeclaration || node.kind == ts.SyntaxKind.ClassDeclaration)
            .map(type => typeMapper.generate(type));
        return {
            types: typeMapper.types,
            policies: [],
            services: []
        };
    }
}
exports.SchemaGenerator = SchemaGenerator;
//# sourceMappingURL=schemaGenerator.js.map