import {TaxiDocument} from "./schema";
import * as _ from 'lodash'

export class SchemaWriter {
   generateSchemas(docs: TaxiDocument[]): string[] {
      return _.flatMap(docs, d => this.generateSchema(d));
   }

   private generateSchema(doc: TaxiDocument): string[] {

   }
}
