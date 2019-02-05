import {expect} from "chai";
import {schemaFromFile} from "./testUtils";

describe("generating services", () => {
   it("should generate a service", () => {
      let schema = schemaFromFile('./test/testServices.ts');
      let service = schema.service("ClientService");
      expect(service).not.to.be.undefined;
      expect(service.qualifiedName).to.equal("ClientService");
      expect(service.operations).to.have.lengthOf(1);
      let operation = service.operations[0];
      expect(operation.name).to.equal("getClient");
      expect(operation.parameters).to.have.lengthOf(1);
      expect(operation.parameters[0].type.qualifiedName).to.equal("foo.EmailAddress");
      expect(operation.returnType.qualifiedName).to.equal("Client")
   });
});
