interface Client {
}
/**
 * @Service
 */
interface ClientService {
    /**
     * @Operation
     */
    getClient(emailAddress: EmailAddress): Client;
}
/**
 * @DataType foo.EmailAddress
 */
declare type EmailAddress = string;
