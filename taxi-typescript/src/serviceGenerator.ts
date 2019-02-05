import {Primitives, TypeMapper} from "./typeGenerator";
import {Operation, Parameter, Service} from "./schema";
import {TypeHelper} from "./typeHelper";
import * as ts from "typescript";
import {TODO} from "./utils";

export interface ServiceMapper {

   services: Service[]
}

export interface OperationProvider {

}

export class DefaultServiceMapper implements ServiceMapper {
   readonly services: Service[];

   constructor(private typeHelper: TypeHelper, private typeMapper: TypeMapper) {
      this.services = this.build();
   }

   private build(): Service[] {
      const serviceNodes = this.typeHelper.getTypesWithJsDocTag("Service");
      return serviceNodes.map(node => this.generateService(node));
      // throw new Error("Not implemented")
   }

   private generateService(node: ts.ObjectTypeDeclaration): Service {
      let operationMembers: Array<ts.ClassElement | ts.TypeElement> = this.typeHelper.getMembersWithJsDocTag(node, "Operation");
      let operations = operationMembers.map(member => this.generateOperation(member));
      let serviceName = this.typeHelper.getObjectTypeName(node);
      return <Service>{
         qualifiedName: serviceName,
         operations: operations,
         annotations: []
      };
   }

   private generateOperation(member: ts.ClassElement | ts.TypeElement): Operation {
      if (ts.isMethodSignature(member)) {
         let name = this.typeHelper.getNameFromIdentifier(member.name as ts.Identifier);
         let returnType = this.typeMapper.getTypeOrDefault(member.type, Primitives.VOID)
         let parameters = member.parameters.map(param => {
            let paramType = this.typeMapper.getTypeOrDefault(param.type, Primitives.ANY);
            let paramName:string | null;
            if (ts.isIdentifier(param.name)) {
               paramName = this.typeHelper.getNameFromIdentifier(param.name)
            } else {
               paramName = null;
            }
            return <Parameter>{
               name: paramName,
               type: paramType,
               annotations: [],
               constraints: [],
               description: ""
            }
         });
         return <Operation>{
            name: name,
            annotations: [], // TODO
            contract: null, // TODO
            parameters: parameters,
            returnType: returnType,
            scope: null // TODO

         }
      } else {
         return TODO("Unhandled method type, cannot generate operation")
      }
      let name = member.name;
      return TODO("error")
   }
}

export interface ServiceGeneratorFactory {
   build(typeHelper: TypeHelper, typeMapper: TypeMapper): ServiceMapper
}

export class DefaultServiceGeneratorFactory implements ServiceGeneratorFactory {
   build(typeHelper: TypeHelper, typeMapper: TypeMapper): ServiceMapper {
      return new DefaultServiceMapper(typeHelper, typeMapper);
   }

}
