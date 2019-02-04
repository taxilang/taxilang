import * as taxi from './schema'
import {SchemaWriter} from "./schemaWriter";

export class SchemaHelper {
   writer: SchemaWriter;

   constructor(readonly doc: taxi.TaxiDocument) {
      this.writer = new SchemaWriter(doc);
   }

   type(name: string): taxi.Type {
      const type = this.doc.types.find((t: taxi.Type) => t.qualifiedName == name);
      if (!type) throw new Error(`No type with name ${name} found`);
      return type;
   }


}
