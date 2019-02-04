/**
 * @DataType
 */
interface Client {
   personName: PersonName
   clientId: string;
   age: number;
   active: boolean;
   referrer: Client; // To ensure circular references are parsed correctly
}

// Note: Ordering is important here..
// Defining PersonName AFTER it's been referenced, to ensure forward-references work
/**
 * @DataType("foo.PersonName")
 * tag to ensure we can use names in tags
 */
interface PersonName {
   firstName: FirstName;
   lastName: string
}

/**
 * @DataType("demo.FirstName")
 */
type FirstName = string
