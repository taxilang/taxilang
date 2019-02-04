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
const chai_1 = require("chai");
const schemaGenerator_1 = require("../src/schemaGenerator");
const ts = __importStar(require("typescript"));
describe("Taxi model generator", () => {
    describe("kitchen sink generation", () => {
        const taxiDoc = schemaFromFile("./test/testModels.ts");
        it("should generate a model for a user type correctly", () => {
            chai_1.expect(taxiDoc.type("Client")).not.to.be.undefined;
            let clientType = taxiDoc.type("Client");
            chai_1.expect(clientType.field("clientId").type.qualifiedName).to.equal("lang.taxi.String");
            chai_1.expect(clientType.field("personName").type.qualifiedName).to.equal("foo.PersonName");
            chai_1.expect(clientType.field("age").type.qualifiedName).to.equal("lang.taxi.Int");
            chai_1.expect(clientType.field("active").type.qualifiedName).to.equal("lang.taxi.Boolean");
            chai_1.expect(clientType.field("referrer").type.qualifiedName).to.equal("Client");
        });
    });
    it("should generate a model with an explicit name correctly", () => {
        const schema = schemaFromSrc(`
/**
 * @DataType("foo.Client")
 */
interface Client {}      
`);
        chai_1.expect(schema.type("foo.Client")).to.not.be.undefined;
    });
    it("should use the class name if no name is provided", () => {
        const schema = schemaFromSrc(`
/**
 * @DataType
 */
interface Client {}
`);
        chai_1.expect(schema.type("Client")).to.not.be.undefined;
    });
    // TODO : Should probably just skip it if the @DataType tag isn't present?
    it("should use the class name if no @DataType tag is present", () => {
        const schema = schemaFromSrc(`
interface Client {}
`);
        chai_1.expect(schema.type("Client")).to.not.be.undefined;
    });
    it("should map inheritence correctly", () => {
        const schema = schemaFromSrc(`
interface Foo {}
/**
 * @DataType("test.Bar")
 */
interface Bar {}
interface Baz extends Foo, Bar
`);
        let type = schema.type("Baz");
        chai_1.expect(type.inheritsFrom).to.have.lengthOf(2);
        chai_1.expect(type.inheritsFrom.some(t => t.qualifiedName == "Foo")).to.be.true;
        chai_1.expect(type.inheritsFrom.some(t => t.qualifiedName == "test.Bar")).to.be.true;
    });
});
function schemaFromFile(fileName) {
    return new schemaHelper_1.SchemaHelper(new schemaGenerator_1.SchemaGenerator([fileName]).generate());
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
    return new schemaHelper_1.SchemaHelper(new schemaGenerator_1.SchemaGenerator(["file.ts"], compilerHost).generate());
}
//# sourceMappingURL=modelGen.spec.js.map