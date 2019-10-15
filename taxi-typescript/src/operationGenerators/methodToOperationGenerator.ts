import {OperationProvider} from "../serviceGenerator";
import {Operation, Parameter} from "../schema";
import * as ts from "typescript";
import {Primitives, TypeMapper} from "../typeGenerator";
import {TypeHelper} from "../typeHelper";

/**
 * Generates operations for methods on classes that have a @operation jsdoc tag,
 */
export class MethodToOperationGenerator implements OperationProvider {
   constructor(private typeHelper: TypeHelper, private typeMapper: TypeMapper) {
   }

   canProvideFor(node: ts.Node): boolean {
      return ts.isMethodSignature(node);
   }

   getOperation(member: ts.Node): Operation {
      if (!ts.isMethodSignature(member)) {
         throw new Error("Cannot generate an operation - looks like the wrong type was provided")
      }
      let name = TypeHelper.getNameFromIdentifier(member.name as ts.Identifier);
      let returnType = this.typeMapper.getTypeOrDefault(member.type, Primitives.VOID)
      let parameters = member.parameters.map(param => {
         let paramType = this.typeMapper.getTypeOrDefault(param.type, Primitives.ANY);
         let paramName: string | null;
         if (ts.isIdentifier(param.name)) {
            paramName = TypeHelper.getNameFromIdentifier(param.name)
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
   }

}
