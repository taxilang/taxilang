// Note: Intentionally not declaring this as a DataType, to
// test exporting members based on usage.
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
