import {QualifiedName} from "../src/types";
import {expect} from "chai";

describe("parsing names", () => {
   it("should parse a qualified name correctly", () => {
      let name = QualifiedName.from("lang.taxi.SomeType");
      expect(name.typeName).to.equal("SomeType");
      expect(name.namespace).to.equal("lang.taxi");
   })
});
