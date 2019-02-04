import {schemaFromFile} from "./modelGen.spec";

describe("Taxi writer", () => {

   describe("Kitchen sink test", () => {
      it("should generate expected taxi for model", () => {
         let writer = schemaFromFile("./test/testModels.ts").writer
         let taxi = writer.generateSchemas();
      });
   })

});
