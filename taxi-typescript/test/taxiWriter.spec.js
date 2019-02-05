"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const testUtils_1 = require("./testUtils");
describe("Taxi writer", () => {
    describe("Kitchen sink test", () => {
        it("should generate expected taxi for model", () => {
            let schema = testUtils_1.schemaFromFile("./test/testModels.ts").schemaText;
            chai_1.expect(schema).to.equal("");
            // let taxi = writer.generateSchemas();
        });
        it("should generate expected taxi for service", () => {
            let schema = testUtils_1.schemaFromFile("./test/testServices.ts").schemaText;
            chai_1.expect(schema).to.equal("");
        });
    });
});
//# sourceMappingURL=taxiWriter.spec.js.map