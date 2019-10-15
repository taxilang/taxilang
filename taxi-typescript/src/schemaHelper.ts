import * as taxi from './schema'
import {Operation, Service} from './schema'
import * as _ from 'lodash'
import {SchemaWriter} from "./schemaWriter";
import {Type} from "./types";
import {TypeHelper} from "./typeHelper";
import {SchemaGenerator} from "./schemaGenerator";

export class SchemaHelper {

   constructor(readonly generator: SchemaGenerator, readonly doc: taxi.TaxiDocument) {
   }

   hasType(name: string): boolean {
      return this.doc.types.some((t: Type) => t.qualifiedName == name);

   }

   get lastTypeHelper(): TypeHelper | undefined {
      return this.generator.lastTypeHelper
   }

   type(name: string): Type {
      const type = this.doc.types.find((t: Type) => t.qualifiedName == name);
      if (!type) throw new Error(`No type with name ${name} found`);
      return type;
   }

   get schemaText(): string {
      return new SchemaWriter().generateSchemas([this.doc]).join("\n").trim()
   }


   operation(name: string): Operation {
      let operations = _.flatMap(this.doc.services, s => s.operations);
      let operation = operations.find(o => o.name == name)
      if (!operation) throw new Error(`No operation with name ${name} found`)
      return operation
   }

   service(name: string): Service {
      const service = this.doc.services.find(s => s.qualifiedName == name);
      if (!service) throw new Error(`No service with name ${name} found`)
      return service;
   }
}
