import {expect} from "chai";
import {ObjectType} from "../src/types";
import {schemaFromFile, schemaFromSrc} from "../src/schemaUtils";

describe("Taxi model generator", () => {
    // describe("kitchen sink generation", () => {
    //     const taxiDoc = schemaFromFile("./test/testModels.ts");
    //     it("should generate a model for a user type correctly", () => {
    //         expect(taxiDoc.type("Client")).not.to.be.undefined;
    //         let clientType = taxiDoc.type("Client") as ObjectType;
    //
    //         expect(clientType.field("clientId").type.qualifiedName).to.equal("lang.taxi.String");
    //         expect(clientType.field("personName").type.qualifiedName).to.equal("foo.PersonName");
    //         expect(clientType.field("age").type.qualifiedName).to.equal("lang.taxi.Int");
    //         expect(clientType.field("active").type.qualifiedName).to.equal("lang.taxi.Boolean");
    //         expect(clientType.field("referrer").type.qualifiedName).to.equal("Client");
    //
    //         expect(taxiDoc.type("demo.FirstName")).not.to.be.undefined;
    //         expect(taxiDoc.type("LastName")).not.to.be.undefined;
    //     });
    //
    // });

    it("should generate a model with an explicit name correctly", () => {
        const schema = schemaFromSrc(`
/**
 * @DataType foo.Client
 */
interface Client {}      
`);
        expect(schema.type("foo.Client")).to.not.be.undefined;
    });

    it("should use the class name if no name is provided", () => {
        const schema = schemaFromSrc(`
/**
 * @DataType
 */
interface Client {}
`);
        expect(schema.type("Client")).to.not.be.undefined;
    });

    it("should not map an interface no @DataType tag is present", () => {
        const schema = schemaFromSrc(`
interface Client {}
`);
        expect(schema.hasType("Client")).to.be.false;
    });

    it("should map inheritence correctly", () => {
        const schema = schemaFromSrc(`
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
        let type = schema.type("Baz") as ObjectType;
        expect(type.inheritsFrom).to.have.lengthOf(2);
        expect(type.inheritsFrom.some(t => t.qualifiedName == "Foo")).to.be.true;
        expect(type.inheritsFrom.some(t => t.qualifiedName == "test.Bar")).to.be.true
    });

    it("should resolve references to array types", () => {
        const schema = schemaFromSrc(`
/**
* @DataType
*/
interface Book {}

/**
* @DataType
*/
interface Library {
   books : Book[]
}
      `);

        let type = schema.type("Library") as ObjectType;
        expect(type.field("books").type.qualifiedName).to.equal("lang.taxi.Array<Book>");
    })

});

