import {TypeMapper} from "./typeGenerator";
import {Operation, Service} from "./schema";
import {TypeHelper} from "./typeHelper";
import * as ts from "typescript";
import {MethodToOperationGenerator} from "./operationGenerators/methodToOperationGenerator";
import * as _ from "lodash";

export interface ServiceMapper {

   services: Service[]
}

export class OperationProviderList implements OperationProvider {
   constructor(private providers: OperationProvider[]) {

   }

   canProvideFor(node: ts.Node): boolean {
      return this.providers.find(p => p.canProvideFor(node)) !== undefined;
   }

   getOperation(node: ts.Node): Operation {
      return this.providers.find(p => p.canProvideFor(node))!
         .getOperation(node);
   }

}

export interface OperationProvider {

   canProvideFor(node: ts.Node): boolean

   getOperation(node: ts.Node): Operation
}

export class DefaultServiceMapper implements ServiceMapper {
   readonly services: Service[];

   constructor(private typeHelper: TypeHelper, private typeMapper: TypeMapper, private operationProviders: OperationProvider[]) {
      this.services = this.build();
   }

   private build(): Service[] {
      const serviceNodes = this.typeHelper.getTypesWithJsDocTag("Service");
      let declaredServices = serviceNodes.map(node => this.generateService(node));
      const servicelesssOperations = this.typeHelper.getNodesWithJsDocTag("operation");
      let defaultServices = this.generateDefaultService(servicelesssOperations);
      return declaredServices.concat(defaultServices);
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

   private generateOperation(member: ts.Node): Operation {
      let provider = this.operationProviders.find(o => o.canProvideFor(member));
      if (!provider) throw new Error("No operation provider was found to generate an operation from source: " + member.getFullText());
      return provider.getOperation(member);
   }

   private generateDefaultService(operationNodes: ts.Node[]): Service[] {
      let operations = operationNodes.map(operationNode => {
         return this.generateOperation(operationNode);
         // TODO : Make this work for scnearios other than serverless
         // TODO : Extract this to a specific serverless-aws impl.
      });

      if (operations.length > 0) {
         return [<Service>{
            qualifiedName: "vyne.DefaultService", // need a better name, this won't work
            operations: operations,
            annotations: []
         }]
      } else {
         return [];
      }


   }
}

export interface ServiceGeneratorFactory {
   build(typeHelper: TypeHelper, typeMapper: TypeMapper, operationFactory: OperationProviderFactory): ServiceMapper
}

export class CompositeOperationProviderFactory implements OperationProviderFactory {
   readonly factories: OperationProviderFactory[] = [
      new DefaultOperationProviderFactory()
   ];

   prependFactory(factory: OperationProviderFactory) {
      this.factories.unshift(factory);
   }

   appendFactory(factory: OperationProviderFactory) {
      this.factories.push(factory)
   }

   build(typeHelper: TypeHelper, typeMapper: TypeMapper): OperationProvider[] {
      return _.flatMap(this.factories, (f: OperationProviderFactory) => f.build(typeHelper, typeMapper));
   }

}

export interface OperationProviderFactory {
   build(typeHelper: TypeHelper, typeMapper: TypeMapper): OperationProvider[];
}

export class DefaultOperationProviderFactory implements OperationProviderFactory {
   build(typeHelper: TypeHelper, typeMapper: TypeMapper): OperationProvider[] {
      return [
         new MethodToOperationGenerator(typeHelper, typeMapper)
      ];
   }

}


export class DefaultServiceGeneratorFactory implements ServiceGeneratorFactory {

   build(typeHelper: TypeHelper, typeMapper: TypeMapper, operationProviderFactory: OperationProviderFactory): ServiceMapper {

      let operationProviders = operationProviderFactory.build(typeHelper, typeMapper);

      return new DefaultServiceMapper(typeHelper, typeMapper, operationProviders);
   }

}
