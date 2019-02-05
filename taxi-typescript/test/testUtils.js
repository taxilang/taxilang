"use strict";
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const schemaHelper_1 = require("../src/schemaHelper");
const schemaGenerator_1 = require("../src/schemaGenerator");
const ts = __importStar(require("typescript"));
function schemaFromFile(fileName) {
    return new schemaHelper_1.SchemaHelper(new schemaGenerator_1.SchemaGenerator().addSources([fileName]).generate());
}
exports.schemaFromFile = schemaFromFile;
function schemaFromSrc(src) {
    let outputs = [];
    let compilerHost = {
        getSourceFile: function (filename, languageVersion) {
            if (filename === "file.ts")
                return ts.createSourceFile(filename, src, ts.ScriptTarget.ESNext);
            if (filename === "lib.d.ts")
                return ts.createSourceFile(filename, "", ts.ScriptTarget.ESNext);
        },
        readFile: function (filename) {
            if (filename === "file.ts")
                return src;
            return undefined;
        },
        writeFile: function (name, text, writeByteOrderMark) {
            outputs.push({ name: name, text: text, writeByteOrderMark: writeByteOrderMark });
        },
        getDefaultLibFileName: function () {
            return "lib.d.ts";
        },
        useCaseSensitiveFileNames: function () {
            return false;
        },
        getCanonicalFileName: function (filename) {
            return filename;
        },
        getCurrentDirectory: function () {
            return "";
        },
        getNewLine: function () {
            return "\n";
        },
        fileExists(fileName) {
            return fileName === "file.ts" || fileName == "lib.d.ts";
        }
    };
    let options = new schemaGenerator_1.SchemaGeneratorOptions();
    options.compilerHost = compilerHost;
    return new schemaHelper_1.SchemaHelper(new schemaGenerator_1.SchemaGenerator(options).addSources(["file.ts"]).generate());
}
exports.schemaFromSrc = schemaFromSrc;
//# sourceMappingURL=testUtils.js.map