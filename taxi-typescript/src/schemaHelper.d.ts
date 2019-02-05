import * as taxi from './schema';
import { Service } from './schema';
import { Type } from "./types";
export declare class SchemaHelper {
    readonly doc: taxi.TaxiDocument;
    constructor(doc: taxi.TaxiDocument);
    hasType(name: string): boolean;
    type(name: string): Type;
    readonly schemaText: string;
    service(name: string): Service;
}
