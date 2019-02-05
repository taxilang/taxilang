import * as taxi from './schema'
import {Service} from './schema'
import {SchemaWriter} from "./schemaWriter";
import {Type} from "./types";

export class SchemaHelper {

   constructor(readonly doc: taxi.TaxiDocument) {
   }

   hasType(name: string): boolean {
      return this.doc.types.some((t: Type) => t.qualifiedName == name);

   }

   type(name: string): Type {
      const type = this.doc.types.find((t: Type) => t.qualifiedName == name);
      if (!type) throw new Error(`No type with name ${name} found`);
      return type;
   }

   get schemaText(): string {
      return new SchemaWriter().generateSchemas([this.doc]).join("\n").trim()
   }


   service(name: string): Service {
      const service = this.doc.services.find(s => s.qualifiedName == name);
      if (!service) throw new Error(`No service with name ${name} found`)
      return service;
   }
}
