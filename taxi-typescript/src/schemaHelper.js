"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const schemaWriter_1 = require("./schemaWriter");
class SchemaHelper {
    constructor(doc) {
        this.doc = doc;
        this.writer = new schemaWriter_1.SchemaWriter(doc);
    }
    type(name) {
        const type = this.doc.types.find((t) => t.qualifiedName == name);
        if (!type)
            throw new Error(`No type with name ${name} found`);
        return type;
    }
}
exports.SchemaHelper = SchemaHelper;
//# sourceMappingURL=schemaHelper.js.map