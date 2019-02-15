import {Annotatable, Field, Operation, Service, TaxiDocument} from "./schema";
import * as _ from 'lodash'
import {
   escapeReservedWords,
   isArrayType,
   isObjectType,
   isTypeAliasType,
   ObjectType,
   QualifiedName,
   TaxiDocuments,
   Type,
   TypeAlias
} from "./types";
import {Primitives} from "./typeGenerator";

export class SchemaWriter {


   generateSchemas(docs: TaxiDocument[]): string[] {
      return _.flatMap(docs, d => this.generateSchema(d));
   }

   private generateSchema(doc: TaxiDocument): string[] {
      let generated: string[] = TaxiDocuments.toNamespacedDoc(doc).map(namespacedDoc => {
         let types: string[] = namespacedDoc.types.map(type => this.generateTypeDeclaration(type, namespacedDoc.namespace));
         let typesTaxiString = types.join("\n\n").trim();

         let services: string[] = namespacedDoc.services.map(service => this.generateServiceDeclaration(service, namespacedDoc.namespace))
         let serviceString = services.join("\n\n").trim();
         let declarations = `
${typesTaxiString}
${serviceString}
         `;

         if (namespacedDoc.namespace) {
            return `namespace ${namespacedDoc.namespace} {
   ${declarations}
   }
            `
         } else {
            return declarations
         }
      });
      return generated;

   }

   private generateTypeDeclaration(type: Type, namespace: string): string {
      if (isObjectType(type)) {
         return this.generateObjectTypeDeclaration(type, namespace);
      } else if (isTypeAliasType(type)) {
         return this.generateTypeAliasDeclaration(type, namespace);
      } else {
         throw new Error("Unhandled type for type : " + type.qualifiedName)
      }

   }

   private generateObjectTypeDeclaration(type: ObjectType, namespace: string): string {
      let fieldDeclarations = type.fields.map(field => this.generateFieldDeclaration(field, namespace)).join("\n");
      let modifiers = type.definition!.modifiers.map(m => m.toString()).join(" ");
      let inheritanceString = this.getInheritenceString(type);

      return `${modifiers} type ${QualifiedName.forType(type).escapedTypeName}${inheritanceString} {
   ${fieldDeclarations}
      }`;
   }

   private generateFieldDeclaration(field: Field, namespace: string): string {
      let fieldType = this.typeAsTaxi(field.type, namespace);

      // TODO : Constraints

      return `${escapeReservedWords(field.name)} : ${fieldType}`.trim()
   }

   private typeAsTaxi(type: Type, namespace: string): string {
      if (isArrayType(type)) {
         return this.typeAsTaxi(type.type, namespace) + "[]"
      } else {
         return QualifiedName.from(type.qualifiedName).qualifyRelativeTo(namespace)
      }
   }

   private getInheritenceString(type: ObjectType): string {
      if (!type.inheritsFrom || type.inheritsFrom.length === 0) {
         return ""
      } else {
         return ` inherits ${type.inheritsFrom.map(t => t.qualifiedName).join(" ")}`
      }
   }

   private generateTypeAliasDeclaration(type: TypeAlias, namespace: string) {
      let aliasTypeString = this.typeAsTaxi(type.aliasType!, namespace);
      return `type alias ${QualifiedName.forType(type).escapedTypeName} as ${aliasTypeString}`
   }

   private generateServiceDeclaration(service: Service, namespace: string): string {
      let operations = service.operations.map(operation => this.generateOperationDeclaration(operation, namespace)).join("\n\n");

      return `
${this.generateAnnotations(service)}
service ${QualifiedName.forType(service).qualifyRelativeTo(namespace)} {
${operations}
}`.trim();

   }

   generateOperationDeclaration(operation: Operation, namespace: string): string {
      let paramsString = operation.parameters.map(param => {
         let constraintString = ""; // TODO
         let annotations = this.generateAnnotations(param); // TODO

         let paramName = (param.name) ? `${param.name} : ` : "";
         let paramDeclaration = this.typeAsTaxi(param.type, namespace);
         return annotations + paramName + paramDeclaration + constraintString
      }).join(", ");
      let returnType: string;
      if (!operation.returnType || operation.returnType === Primitives.VOID) {
         returnType = ""
      } else {
         let returnTypeName = this.typeAsTaxi(operation.returnType, namespace);
         let returnContract = ""; // TODO
         returnType = (" : " + returnTypeName + returnContract)
      }

      let scope = (operation.scope) ? operation.scope + " " : "";
      let annotations = this.generateAnnotations(operation);
      return `${annotations}
   ${scope}operation ${operation.name}( ${paramsString} )${returnType}`.trim()
   }

   private quoteIfNeeded(param: any): string {
      if (typeof param === "boolean") {
         return param.toString()
      } else if (this.isNumber(param)) {
         return param.toString()
      } else {
         return `"${param.toString()}"`
      }
   }

   private isNumber(value: any): boolean {
      return !isNaN(Number(value.toString()));
   }

   private generateAnnotations(element: Annotatable): string {
      return element.annotations.map(annotation => {
         let paramKeys = Object.keys(annotation.parameters);
         if (paramKeys.length === 0) {
            return "@" + annotation.name
         }
         let annotationParams = paramKeys.map(key => {
            return `${key} = ${this.quoteIfNeeded(annotation.parameters[key]!)}`
         }).join(", ");
         return `@${annotation.name}(${annotationParams})`
      }).join("\n")
   }
}

