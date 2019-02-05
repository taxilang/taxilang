"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const schemaWriter_1 = require("./schemaWriter");
class SchemaHelper {
    constructor(doc) {
        this.doc = doc;
    }
    hasType(name) {
        return this.doc.types.some((t) => t.qualifiedName == name);
    }
    type(name) {
        const type = this.doc.types.find((t) => t.qualifiedName == name);
        if (!type)
            throw new Error(`No type with name ${name} found`);
        return type;
    }
    get schemaText() {
        return new schemaWriter_1.SchemaWriter().generateSchemas([this.doc]).join("\n").trim();
    }
    service(name) {
        const service = this.doc.services.find(s => s.qualifiedName == name);
        if (!service)
            throw new Error(`No service with name ${name} found`);
        return service;
    }
}
exports.SchemaHelper = SchemaHelper;
//# sourceMappingURL=schemaHelper.js.map