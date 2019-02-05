"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const testUtils_1 = require("./testUtils");
describe("Taxi model generator", () => {
    describe("kitchen sink generation", () => {
        const taxiDoc = testUtils_1.schemaFromFile("./test/testModels.ts");
        it("should generate a model for a user type correctly", () => {
            chai_1.expect(taxiDoc.type("Client")).not.to.be.undefined;
            let clientType = taxiDoc.type("Client");
            chai_1.expect(clientType.field("clientId").type.qualifiedName).to.equal("lang.taxi.String");
            chai_1.expect(clientType.field("personName").type.qualifiedName).to.equal("foo.PersonName");
            chai_1.expect(clientType.field("age").type.qualifiedName).to.equal("lang.taxi.Int");
            chai_1.expect(clientType.field("active").type.qualifiedName).to.equal("lang.taxi.Boolean");
            chai_1.expect(clientType.field("referrer").type.qualifiedName).to.equal("Client");
            chai_1.expect(taxiDoc.type("demo.FirstName")).not.to.be.undefined;
            chai_1.expect(taxiDoc.type("LastName")).not.to.be.undefined;
        });
    });
    it("should generate a model with an explicit name correctly", () => {
        const schema = testUtils_1.schemaFromSrc(`
/**
 * @DataType foo.Client
 */
interface Client {}      
`);
        chai_1.expect(schema.type("foo.Client")).to.not.be.undefined;
    });
    it("should use the class name if no name is provided", () => {
        const schema = testUtils_1.schemaFromSrc(`
/**
 * @DataType
 */
interface Client {}
`);
        chai_1.expect(schema.type("Client")).to.not.be.undefined;
    });
    it("should not map an interface no @DataType tag is present", () => {
        const schema = testUtils_1.schemaFromSrc(`
interface Client {}
`);
        chai_1.expect(schema.hasType("Client")).to.be.false;
    });
    it("should map inheritence correctly", () => {
        const schema = testUtils_1.schemaFromSrc(`
interface Foo {}
/**
 * @DataType test.Bar
 */
interface Bar {}

/**
 * @DataType
 */
interface Baz extends Foo, Bar
`);
        let type = schema.type("Baz");
        chai_1.expect(type.inheritsFrom).to.have.lengthOf(2);
        chai_1.expect(type.inheritsFrom.some(t => t.qualifiedName == "Foo")).to.be.true;
        chai_1.expect(type.inheritsFrom.some(t => t.qualifiedName == "test.Bar")).to.be.true;
    });
});
//# sourceMappingURL=modelGen.spec.js.map