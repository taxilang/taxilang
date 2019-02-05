"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const types_1 = require("../src/types");
const chai_1 = require("chai");
describe("parsing names", () => {
    it("should parse a qualified name correctly", () => {
        let name = types_1.QualifiedName.from("lang.taxi.SomeType");
        chai_1.expect(name.typeName).to.equal("SomeType");
        chai_1.expect(name.namespace).to.equal("lang.taxi");
    });
});
//# sourceMappingURL=types.spec.js.map