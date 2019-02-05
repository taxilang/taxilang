import {expect} from "chai";
import {schemaFromFile} from "./testUtils";

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
      })
   })

});

