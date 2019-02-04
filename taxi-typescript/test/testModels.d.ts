/**
 * @DataType
 */
interface Client {
    personName: PersonName;
    clientId: string;
    age: number;
    active: boolean;
    referrer: Client;
}
/**
 * @DataType("foo.PersonName")
 * tag to ensure we can use names in tags
 */
interface PersonName {
    firstName: FirstName;
    lastName: string;
}
/**
 * @DataType("demo.FirstName")
 */
declare type FirstName = string;
