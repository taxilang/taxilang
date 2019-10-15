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
 * @DataType foo.PersonName
 * tag to ensure we can use names in tags
 */
interface PersonName {
   firstName: FirstName;
   lastName: LastName
}

/**
 * @DataType demo.FirstName
 */
type FirstName = string

// Doesn't have a datatype tag, but should still be exported, as is referenced
type LastName = string
