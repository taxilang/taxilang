import {expect} from "chai";
import {Annotation, Operation, Primitives, schemaFromFile, schemaFromSrc, SchemaWriter, Type, TypeKind} from "../src";

describe("Taxi writer", () => {

    describe("Kitchen sink test", () => {
        it("should generate expected taxi for model", () => {
            let schema = schemaFromFile("./test/testModels.ts").schemaText;
            expect(schema).to.equal("")
            // let taxi = writer.generateSchemas();
        });

        it("should generate expected taxi for service", () => {
            let schema = schemaFromFile("./test/testServices.ts").schemaText;
            expect(schema).to.equal("")
        });

        it("should generate arrays correctly", () => {
           let schema = schemaFromSrc(`
/**
 * @DataType
 */
 interface Person {
    friends : Person[]
 }`).schemaText;
            expect(noSpace(schema)).to.equal(noSpace(`
type Person {
   friends : Person[]
}`))

        });

        it("should generate expected taxi for an operation", () => {
                let operation = <Operation>{
                    annotations: [annotation("HttpOperation", {"method": "GET", "baseUrl": "http://foo.bar/{SomeType}"})],
                    name: "MyOperation",
                    parameters: [
                        {
                            name: "firstArg",
                            type: <Type>{
                                qualifiedName: "foo.Client",
                                kind: TypeKind.PrimitiveType
                            },
                            annotations: [],
                            constraints: [],
                            description: ""
                        },
                        {
                            name: null,
                            type: <Type>{
                                qualifiedName: "foo.Bar",
                                kind: TypeKind.PrimitiveType
                            },
                            annotations: [annotation("RequestBody", {})],
                        }],
                    returnType: Primitives.STRING,
                    contract: null,
                    scope: null

                };

                let operationTaxi = new SchemaWriter().generateOperationDeclaration(operation, "test.namespace");
                let expected = `
@HttpOperation(method = "GET", baseUrl = "http://foo.bar/{SomeType}")
operation MyOperation( firstArg : foo.Client, @RequestBody foo.Bar ) : lang.taxi.String
            `.trim();
                expect(noSpace(operationTaxi)).to.equal(noSpace(expected))
            }
        )
    })

});

/**
 * Removes all whitespace
 * @param input
 */
function noSpace(input: string): string {
    return input.replace(/ +?/g, '').replace(/(\n)+/g,'');
}

function annotation(name: string, parameters: { [key: string]: any }): Annotation {
    return <Annotation>{
        name,
        parameters
    }
}