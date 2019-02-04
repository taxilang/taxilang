import * as taxi from './schema';
import { SchemaWriter } from "./schemaWriter";
export declare class SchemaHelper {
    readonly doc: taxi.TaxiDocument;
    writer: SchemaWriter;
    constructor(doc: taxi.TaxiDocument);
    type(name: string): taxi.Type;
}
