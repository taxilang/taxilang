import {schemaFromSrc} from "../src/schemaUtils"
import * as ts from "typescript"
import {expect} from "chai";

describe("typeHelper", () => {
   it("should get npm package name of imported type", () => {
      let schema = schemaFromSrc(`
import {APIGatewayEvent, Callback, Context, Handler} from "aws-lambda";

const lambdaHandler: Handler = (event, context: Context, callback: Callback) => {
   // No real impl.
};
      `);
      let typeHelper = schema.lastTypeHelper!;
      let lambdaHandlerStatementNode = (typeHelper.nodes[1] as ts.VariableStatement); // TODO : This is pretty brittle, find a better way to find that node.
      let lambdaHandlerDecl = lambdaHandlerStatementNode.declarationList.declarations[0];
      let typescriptName = typeHelper.getFullyQualifiedName(lambdaHandlerDecl);
      throw new Error("")
   });

   it("should find an exported node by name", () => {
      let schema = schemaFromSrc(`
import {APIGatewayEvent, Callback, Context, Handler} from "aws-lambda";

const lambdaHandler: Handler = (event, context: Context, callback: Callback) => {
   // No real impl.
};

export { lambdaHandler }      
      `, "handler.ts");
      let typeHelper = schema.lastTypeHelper!;
      let node = typeHelper.findExportedNode("handler.lambdaHandler");
      expect(node).not.to.be.undefined;
      expect(ts.isVariableDeclaration(node)).to.be.true
   })
})
;
