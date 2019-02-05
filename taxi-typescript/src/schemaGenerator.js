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
const serviceGenerator_1 = require("./serviceGenerator");
const typeHelper_1 = require("./typeHelper");
class SchemaGeneratorOptions {
    constructor() {
        this.typeMapperFactory = new typeGenerator_1.DefaultTypeMapperFactory();
        this.serviceMapperFactory = new serviceGenerator_1.DefaultServiceGeneratorFactory();
    }
}
exports.SchemaGeneratorOptions = SchemaGeneratorOptions;
class SchemaGenerator {
    constructor(options = new SchemaGeneratorOptions()) {
        // public readonly typeChecker: ts.TypeChecker;
        // private readonly program: ts.Program;
        // private readonly nodes: ts.Node[] = [];
        this.sourceFiles = [];
        this.typeMapperFactory = options.typeMapperFactory;
        this.serviceMapperFactory = options.serviceMapperFactory;
        this.compilerHost = options.compilerHost;
    }
    // constructor(entryFiles: string[], compilerHost?: ts.CompilerHost) {
    //    this.program = ts.createProgram(entryFiles, {}, compilerHost);
    //    // this.typeChecker = this.program.getTypeChecker()
    // }
    addSources(entryFiles) {
        entryFiles.forEach(f => this.sourceFiles.push(f));
        return this;
    }
    generate() {
        let typeHelper = this.createTypeHelper();
        const typeMapper = this.typeMapperFactory.build(typeHelper);
        const serviceMapper = this.serviceMapperFactory.build(typeHelper, typeMapper);
        return {
            types: typeMapper.types,
            policies: [],
            services: serviceMapper.services
        };
    }
    createTypeHelper() {
        let program = ts.createProgram(this.sourceFiles, {}, this.compilerHost);
        let nodes = [];
        program.getSourceFiles().forEach(sourceFile => {
            if (!sourceFile.isDeclarationFile) {
                ts.forEachChild(sourceFile, node => {
                    nodes.push(node);
                });
            }
        });
        return new typeHelper_1.TypeHelper(nodes);
    }
}
exports.SchemaGenerator = SchemaGenerator;
//# sourceMappingURL=schemaGenerator.js.map