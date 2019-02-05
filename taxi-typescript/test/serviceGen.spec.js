"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const testUtils_1 = require("./testUtils");
describe("generating services", () => {
    it("should generate a service", () => {
        let schema = testUtils_1.schemaFromFile('./test/testServices.ts');
        let service = schema.service("ClientService");
        chai_1.expect(service).not.to.be.undefined;
        chai_1.expect(service.qualifiedName).to.equal("ClientService");
        chai_1.expect(service.operations).to.have.lengthOf(1);
        let operation = service.operations[0];
        chai_1.expect(operation.name).to.equal("getClient");
        chai_1.expect(operation.parameters).to.have.lengthOf(1);
        chai_1.expect(operation.parameters[0].type.qualifiedName).to.equal("foo.EmailAddress");
        chai_1.expect(operation.returnType.qualifiedName).to.equal("Client");
    });
});
//# sourceMappingURL=serviceGen.spec.js.map