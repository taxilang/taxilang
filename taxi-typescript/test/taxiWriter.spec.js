"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const modelGen_spec_1 = require("./modelGen.spec");
describe("Taxi writer", () => {
    describe("Kitchen sink test", () => {
        it("should generate expected taxi for model", () => {
            let writer = modelGen_spec_1.schemaFromFile("./test/testModels.ts").writer;
            let taxi = writer.generateSchemas();
        });
    });
});
//# sourceMappingURL=taxiWriter.spec.js.map