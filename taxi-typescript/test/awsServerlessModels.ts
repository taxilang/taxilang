// Note: Intentionally not declaring this as a DataType, to
// test exporting members based on usage.
import {APIGatewayEvent, APIGatewayProxyHandler, Callback, Context, Handler} from "aws-lambda";
// import {AwsApiGatewayRequestResponse} from "../src/operationGenerators/serverlessAwsLambdaGenerator";

interface Client {

}

interface ClientSearchRequest {
}

/**
 * @operation
 */
const handlerWithReturnType: Handler<APIGatewayEvent, Client> = (event, context: Context, callback: Callback<Client>) => {
};

/**
 * @operation
 */
const handlerWithoutReturnType: Handler = (event, context: Context, callback: Callback) => {
};

// const handlerWithRequestAndResponseType: AwsApiGatewayRequestResponse<ClientSearchRequest, Client> = (event, context: Context, callback: Callback<Client>) => {
// };

export {
   handlerWithReturnType
}
