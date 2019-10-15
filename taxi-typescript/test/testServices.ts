// Note: Intentionally not declaring this as a DataType, to
// test exporting members based on usage.
import {APIGatewayEvent, Callback, Context, Handler} from "aws-lambda";

interface Client {

}

/**
 * @Service
 */
interface ClientService {

   /**
    * @Operation
    */
   getClient(emailAddress: EmailAddress): Client
}

/**
 * @DataType foo.EmailAddress
 */
type EmailAddress = string

/**
 * @operation
 */
const findClientById: Handler<APIGatewayEvent, Client> = (event, context: Context, callback: Callback<Client>) => {
   // No real impl.
};
