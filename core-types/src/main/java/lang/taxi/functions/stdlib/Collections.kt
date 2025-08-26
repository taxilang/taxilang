package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(
      Contains,
      AllOf,
      AnyOf,
      NoneOf,
      All,
      Any,
      None,
      Single,
      Filter,
      FilterEach,
      SingleBy,
      First,
      ExactlyOne,
      Last,
      GetAtIndex,
      Intersection,
      ListOf,
      JoinToString,
      IfEmpty,
      OrEmpty,
      Append
   )
}

object NoneOf : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` only if all provided boolean values are `false`. Equivalent to logical NOR.

      This function is useful for checking if none of multiple conditions apply.

      ```taxi
      // Check if a user has no permissions
      type NoPermissions inherits Boolean by noneOf(IsAdmin, IsEditor, IsViewer)
      ```
      ]]
      declare function noneOf(values:Boolean...): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("noneOf")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `noneOf()` to verify if a user lacks all specified roles.""",
         query = StubQueryMessage(
            schema = """
               model User {
                  username: String
                  isAdmin: Boolean
                  isEditor: Boolean
                  isViewer: Boolean
               }
               type HasNoAccess inherits Boolean by noneOf(isAdmin, isEditor, isViewer)
            """.trimIndent(),
            query = """
               given {
                  user: User = {
                     username: 'GuestUser',
                     isAdmin: false,
                     isEditor: false,
                     isViewer: false
                  }
               }
               find {
                  username: user.username
                  noAccess: noneOf(user.isAdmin, user.isEditor, user.isViewer)
               }
            """.trimIndent(),
            expectedJson = """{
               "username": "GuestUser",
               "noAccess": true
            }"""
         )
      )
   )
}

object AnyOf : FunctionApi, HasRunnableExamples {
   override val name: QualifiedName = stdLibName("anyOf")
   override val taxi: String = """
      [[
      Returns `true` if at least one of the provided boolean values is `true`. Equivalent to logical OR.

      This function evaluates multiple conditions and returns true if any of them are true.

      ```taxi
      // Check if a user has any account verification
      type IsVerified inherits Boolean by anyOf(HasEmailVerification, HasPhoneVerification, HasIdVerification)
      ```
      ]]
      declare function anyOf(values:Boolean...): Boolean""".trimIndent()

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `anyOf()` to verify if a user meets any of several verification criteria.""",
         query = StubQueryMessage(
            schema = """
               model User {
                  name: String
                  hasEmailVerification: Boolean
                  hasPhoneVerification: Boolean
                  hasIdVerification: Boolean
               }
               type IsVerified inherits Boolean by anyOf(hasEmailVerification, hasPhoneVerification, hasIdVerification)
            """.trimIndent(),
            query = """
               given {
                  user: User = {
                     name: 'Alice',
                     hasEmailVerification: false,
                     hasPhoneVerification: true,
                     hasIdVerification: false
                  }
               }
               find {
                  name: user.name
                  isVerified: anyOf(user.hasEmailVerification, user.hasPhoneVerification, user.hasIdVerification)
               }
            """.trimIndent(),
            expectedJson = """{
               "name": "Alice",
               "isVerified": true
            }"""
         )
      )
   )
}

object None : FunctionApi {
   override val taxi: String =
      """[[ Returns true if none of the items in the collection satisfy the predicate ]]
         declare extension function <T> none(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("none")
}

object Any : FunctionApi {
   override val taxi: String =
      """[[ Returns true if any of the items in the collection satisfy the predicate ]]
         declare extension function <T> any(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("any")
}


object AllOf : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` only if all provided boolean values are `true`. Equivalent to logical AND.

      This function ensures that every condition in a set must be satisfied.

      ```taxi
      // Check if a user can purchase a premium item
      type CanPurchasePremium inherits Boolean by allOf(IsActive, HasSufficientFunds, IsVerified)
      ```
      ]]
      declare function allOf(values:Boolean...): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("allOf")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `allOf()` to verify if a user meets all required criteria for premium access.""",
         query = StubQueryMessage(
            schema = """
               model Customer {
                  name: String
                  isActive: IsActive inherits Boolean
                  hasSufficientFunds: HasSufficientFunds inherits Boolean
                  isVerified: IsVerified inherits Boolean
               }
               type HasPremiumAccess inherits Boolean by allOf(IsActive, HasSufficientFunds, IsVerified)
            """.trimIndent(),
            query = """
               given {
                  customer: Customer = {
                     name: 'Bob',
                     isActive: true,
                     hasSufficientFunds: true,
                     isVerified: true
                  }
               }
               find {
                  name: customer.name
                  hasPremiumAccess: HasPremiumAccess
               }
            """.trimIndent(),
            expectedJson = """{
               "name": "Bob",
               "hasPremiumAccess": true
            }"""
         )
      )
   )
}

object All : FunctionApi {
   override val taxi: String =
      """[[ Returns true if all of the items in the collection satisfy the predicate ]]
         declare extension function <T> all(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("all")
}

object Contains : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the collection contains the specified search target element.

      This function checks whether a given element exists within an array or collection.

      ```taxi
      // Check if a user's roles include admin privileges
      find { user: User } as {
         hasAdminAccess: user.roles.contains("ADMIN")
      }
      ```
      ]]
      declare extension function <T> contains(collection: T[], searchTarget:T): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("contains")

   override val examples: List<DocsSnippet> = listOf(

      DocsSnippet(
         markdown = """undefined""",
         query = StubQueryMessage(
            schema = """type Role inherits String
model User {
   username: Username inherits String
   roles: Role[]
}
          """.trimIndent(),
            query = """
          given {
   user: User = {
      username: 'AdminUser',
      roles: ['USER', 'EDITOR', 'ADMIN']
   }
}
find {
   username: user.username
   // Using type selector
   isAdmin: Boolean = Role[].contains("ADMIN")
   // Using field access
   isGuest: Boolean = user.roles.contains("GUEST")
}
          """.trimIndent(),
            expectedJson = """{
               "username": "AdminUser",
               "isAdmin": true,
               "isGuest": false
            }"""
         )

      )
   )
}

object ExactlyOne : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the only item from the provided collection, or throws an error if there isn't exactly one item in the collection.

      This function is useful when you expect a collection to contain precisely one element and want to retrieve it.

      ```taxi
      // Get the single active session for a user
      find { user: User } as {
         currentSession: user.sessions.filter((session) -> session.isActive).exactlyOne()
      }
      ```
      ]]
      declare extension function <T> exactlyOne(collection:T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("exactlyOne")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `exactlyOne()` to retrieve the single active session for a user.""",
         query = StubQueryMessage(
            schema = """
model Session {
   id: SessionId inherits String
   isActive: IsActive inherits Boolean
}

model User {
   username: Username inherits String
   sessions: Session[]
}
            """.trimIndent(),
            query = """
               given {
                  user: User = {
                     username: 'ActiveUser',
                     sessions: [
                        { id: 'session-1', isActive: true },
                        { id: 'session-2', isActive: false }
                     ]
                  }
               }
               find {
                  username: user.username
                  activeSessionId: SessionId = user.sessions.filter((Session) -> IsActive).exactlyOne().id
               }
            """.trimIndent(),
            expectedJson = """{
               "username": "ActiveUser",
               "activeSessionId": "session-1"
            }"""
         )
      )
   )
}

object Single : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the first item from a collection that matches the provided predicate. Throws an error if no matching item is found.

      This function is useful when you need to find a specific element in a collection based on a condition.

      ```taxi
      // Find a product by its SKU code
      find { inventory: Inventory } as {
         appleMacbook: inventory.products.single((product) -> product.sku == "MB-PRO-2023")
      }
      ```
      ]]
      declare extension function <T> single(collection:T[], callback: (T) -> Boolean):T""".trimIndent()
   override val name: QualifiedName = stdLibName("single")

   override val examples: List<DocsSnippet> = listOf(

      DocsSnippet(
         markdown = """undefined""",
         query = StubQueryMessage(
            schema = """model Product {
   name: ProductName inherits String
   sku: SKU inherits String
   price: Price inherits Decimal
}

model Inventory {
   products: Product[]
}
          """.trimIndent(),
            query = """
          given {
   inventory: Inventory = {
      products: [
         { name: 'Laptop', sku: 'LT-001', price: 1200.00 },
         { name: 'Smartphone', sku: 'SP-002', price: 999.99 },
         { name: 'Tablet', sku: 'TB-003', price: 499.50 }
      ]
   }
}
find {
   smartphoneDetails: Product = inventory.products.single((product: Product) -> product.sku == 'SP-002')
}
          """.trimIndent(),
            expectedJson = """{
               "smartphoneDetails": {
                  "name": "Smartphone",
                  "sku": "SP-002",
                  "price": 999.99
               }
            }"""
         )

      )
   )
}

object SingleBy : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the item from a collection where the value returned by the grouping function matches the search value.

      Similar to `single()`, but results are first grouped by the selector function, and these results are cached
      to improve future performance when querying the same collection multiple times.

      ```taxi
      // Find a product by its SKU code using a cached search
      find { inventory: Inventory } as {
         appleMacbook: inventory.products.singleBy((product) -> product.sku, "MB-PRO-2023")
      }
      ```
      ]]
      declare extension function <T,A> singleBy(collection:T[], groupingFunction: (T) -> A, searchValue: A):T""".trimIndent()
   override val name: QualifiedName = stdLibName("singleBy")

   override val examples: List<DocsSnippet> = listOf(

      DocsSnippet(
         markdown = """undefined""",
         query = StubQueryMessage(
            schema = """model Product {
   name: Name inherits String
   sku: SKU inherits String
}

model Inventory {
   products: Product[]
}
          """.trimIndent(),
            query = """
          given {
   inventory: Inventory = {
      products: [
         { name: 'Laptop', sku: 'LT-001'},
         { name: 'Smartphone', sku: 'SP-002'},
         { name: 'Tablet', sku: 'TB-003' }
      ]
   }
}
find {
   // Find products by SKU code
   laptop: inventory.products.singleBy((product:Product) -> product.sku, 'LT-001' as SKU)
   // Alternative syntax, using types for selection
   tablet: inventory.products.singleBy((Product) -> SKU, 'TB-003' as SKU)
}
          """.trimIndent(),
            expectedJson = """{
   "laptop": {
      "name": "Laptop",
      "sku": "LT-001"
   },
   "tablet": {
      "name": "Tablet",
      "sku": "TB-003"
   }
}"""
         )

      )
   )
}

object First : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the first item within the collection. Throws an error if the collection is empty.

      This function retrieves the first element in an ordered collection.

      ```taxi
      // Get the most recent transaction from a sorted list
      find { account: Account } as {
         latestTransaction: account.transactions.first()
      }
      ```
      ]]
      declare extension function <T> first(collection: T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("first")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `first()` to retrieve the most recent transaction for an account.""",
         query = StubQueryMessage(
            schema = """
               model Transaction {
                  date: String
                  amount: Decimal
                  description: String
               }

               model Account {
                  accountNumber: String
                  transactions: Transaction[] // Assumed to be in descending date order
               }
            """.trimIndent(),
            query = """
               given {
                  account: Account = {
                     accountNumber: 'ACC-12345',
                     transactions: [
                        { date: '2023-05-01', amount: 150.00, description: 'Grocery Store' },
                        { date: '2023-04-28', amount: 45.99, description: 'Online Shop' },
                        { date: '2023-04-25', amount: 20.00, description: 'Transfer' }
                     ]
                  }
               }
               find {
                  accountNumber: account.accountNumber
                  mostRecentTransaction: Transaction = account.transactions.first()
               }
            """.trimIndent(),
            expectedJson = """{
               "accountNumber": "ACC-12345",
               "mostRecentTransaction": {
                  "date": "2023-05-01",
                  "amount": 150.00,
                  "description": "Grocery Store"
               }
            }"""
         )
      )
   )
}

object Last : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the last item within the collection. Throws an error if the collection is empty.

      This function retrieves the final element in an ordered collection.

      ```taxi
      // Get the oldest transaction from a date-sorted list
      find { account: Account } as {
         oldestTransaction: account.transactions.last()
      }
      ```
      ]]
      declare extension function <T> last(collection: T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("last")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `last()` to retrieve the oldest transaction for an account.""",
         query = StubQueryMessage(
            schema = """
               model Transaction {
                  date: String
                  amount: Decimal
                  description: String
               }

               model Account {
                  accountNumber: String
                  transactions: Transaction[] // Assumed to be in descending date order (newest first)
               }
            """.trimIndent(),
            query = """
given {
   account: Account = {
      accountNumber: 'ACC-12345',
      transactions: [
         { date: '2023-05-01', amount: 150.00, description: 'Grocery Store' },
         { date: '2023-04-28', amount: 45.99, description: 'Online Shop' },
         { date: '2023-04-25', amount: 20.00, description: 'Transfer' }
      ]
   }
}
find {
   accountNumber: account.accountNumber
   // Using .last() against the type
   oldestTransaction: Transaction =  Transaction[].last()
   // or, alternatively, using against the property name
   alsoOldestTransaction: Transaction = account.transactions.last()
}
            """.trimIndent(),
            expectedJson = """{
   "accountNumber": "ACC-12345",
   "oldestTransaction": {
      "date": "2023-04-25",
      "amount": 20,
      "description": "Transfer"
   },
   "alsoOldestTransaction": {
      "date": "2023-04-25",
      "amount": 20,
      "description": "Transfer"
   }
}"""
         )
      )
   )
}

object GetAtIndex : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the item at the provided index in the collection. Throws an error if the index is out of bounds.

      This function retrieves an element at a specific position in a collection, with zero-based indexing.

      ```taxi
      // Get the second item in a list
      find { playlist: Playlist } as {
         secondSong: playlist.songs.getAtIndex(1) // Note: index starts at 0
      }
      ```
      ]]
      declare extension function <T> getAtIndex(collection: T[], index: Int):T""".trimIndent()
   override val name: QualifiedName = stdLibName("getAtIndex")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `getAtIndex()` to retrieve a specific song from a playlist by its position.""",
         query = StubQueryMessage(
            schema = """
               model Song {
                  title: String
                  artist: String
                  duration: Int
               }

               model Playlist {
                  name: String
                  songs: Song[]
               }
            """.trimIndent(),
            query = """
               given {
                  playlist: Playlist = {
                     name: 'Road Trip Mix',
                     songs: [
                        { title: 'Highway Star', artist: 'Deep Purple', duration: 372 },
                        { title: 'Born to Run', artist: 'Bruce Springsteen', duration: 270 },
                        { title: 'Life in the Fast Lane', artist: 'Eagles', duration: 251 }
                     ]
                  }
               }
               find {
                  playlistName: playlist.name
                  // Using property
                  firstSong: Song = playlist.songs.getAtIndex(0)
                  // using a type
                  secondSong: Song = Song[].getAtIndex(1)
               }
            """.trimIndent(),
            expectedJson = """{
               "playlistName": "Road Trip Mix",
               "firstSong": {
                  "title": "Highway Star",
                  "artist": "Deep Purple",
                  "duration": 372
               },
               "secondSong": {
                  "title": "Born to Run",
                  "artist": "Bruce Springsteen",
                  "duration": 270
               }
            }"""
         )
      )
   )
}

object Filter : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns a new collection containing only the members of the original collection that match the provided predicate.

      This function is used to select a subset of elements from a collection based on a condition.

      ```taxi
      find { store: Store } as {
         allProducts: store.products // all products
         inStockProducts: store.products.filter((product) -> product.stockLevel > 0)
         affordableProducts: store.products.filter((product) -> product.price < 50.00)
      }
      ```
      ]]
      declare extension function <T> filter(collection:T[], callback: (T) -> Boolean):T[]""".trimIndent()
   override val name: QualifiedName = stdLibName("filter")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `filter()` to find friends who meet a certain age criteria.""",
         query = StubQueryMessage(
            schema = """
               model Friend {
                  name: Name inherits String
                  age: Age inherits Int
               }
            """.trimIndent(),
            query = """
               given {
                  friends: Friend[] = [
                     { name: 'Jim', age: 20 },
                     { name: 'Jack', age: 80 },
                     { name: 'Alice', age: 75 },
                     { name: 'Bob', age: 30 }
                  ]
               }
               find {
                  // Names of seniors (over 70)
                  seniorFriends: Name[] = friends.filter((friend:Friend) -> friend.age > 70) as Name[]
                  // Names of young adults (under 30)
                  youngFriends: Name[] = friends.filter((friend:Friend) -> friend.age < 30) as Name[]
               }
            """.trimIndent(),
            expectedJson = """{
               "seniorFriends": [
                  "Jack",
                  "Alice"
               ],
               "youngFriends": [
                  "Jim"
               ]
            }"""
         )
      )
   )
}

object FilterEach : FunctionApi {
   override val taxi: String = """
      [[
      Evaluates the predicate against the provided item, returning the item if the predicate returns true, or null otherwise.

      This function is intended for use in filtering streams, where null values are automatically excluded.

      ```taxi
      // Filter individual items in a pipeline
      find { items: Item[] } as {
         processedItems: items.map((item) -> filterEach(item, (i) -> i.status == "READY"))
      }
      ```
      ]]
      declare extension function <T> filterEach(item: T, callback: (T) -> Boolean):T?""".trimIndent()
   override val name: QualifiedName = stdLibName("filterEach")
}

object Intersection : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns a new collection containing only the elements that are present in both of the provided collections.

      This function identifies common elements between two collections.

      ```taxi
      // Find common tags between two products
      find { productA: Product, productB: Product } as {
         commonTags: intersection(productA.tags, productB.tags)
      }
      ```
      ]]
      declare extension function <T> intersection(collectionA: T[], collectionB: T[]):T[]""".trimIndent()
   override val name: QualifiedName = stdLibName("intersection")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `intersection()` to find common elements between two collections.""",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
                  tags: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  productA: Product = {
                     name: 'Smartphone',
                     tags: ['electronics', 'mobile', 'camera', 'touchscreen']
                  },
                  productB: Product = {
                     name: 'Digital Camera',
                     tags: ['electronics', 'camera', 'photography', 'zoom']
                  }
               }
               find {
                  productAName: productA.name
                  productBName: productB.name
                  commonTags: intersection(productA.tags, productB.tags)
               }
            """.trimIndent(),
            expectedJson = """{
               "productAName": "Smartphone",
               "productBName": "Digital Camera",
               "commonTags": [
                  "electronics",
                  "camera"
               ]
            }"""
         )
      )
   )
}

object ListOf : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Creates and returns an array containing all the provided values.

      This function is useful for creating collections inline without having to define them separately.

      ```taxi
      // Create a list of allowed status values
      find { order: Order } as {
         isValidStatus: listOf('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED').contains(order.status)
      }
      ```
      ]]
      declare function <T> listOf(values:T...):T[]""".trimIndent()
   override val name: QualifiedName = stdLibName("listOf")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates how to use `listOf()` to create an inline collection and validate against it.""",
         query = StubQueryMessage(
            schema = """
               model Order {
                  id: String
                  status: String
               }
            """.trimIndent(),
            query = """
               given {
                  orders: Order[] = [
                     { id: 'order-1', status: 'PENDING' },
                     { id: 'order-2', status: 'SHIPPED' },
                     { id: 'order-3', status: 'CANCELED' }
                  ]
               }
               find {
                  validOrders: Order[] = orders.filter((order:Order) ->
                     listOf('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED').contains(order.status)
                  )
                  invalidOrders: Order[] =  orders.filter((order:Order) ->
                     listOf('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED').contains(order.status) == false
                  )
               }
            """.trimIndent(),
            expectedJson = """{
               "validOrders": [
                  {
                     "id": "order-1",
                     "status": "PENDING"
                  },
                  {
                     "id": "order-2",
                     "status": "SHIPPED"
                  }
               ],
               "invalidOrders": [
                  {
                     "id": "order-3",
                     "status": "CANCELED"
                  }
               ]
            }"""
         )
      )
   )
}

object JoinToString : FunctionApi {
   override val taxi: String = """
   [[ Creates a string from all the elements separated using separator and using the given prefix and postfix if supplied. Null values are omitted ]]
   declare extension function <T> joinToString(values:T[], separator: String = ",", prefix: String? = null, postfix: String? = null): String
   """.trimIndent()

   override val name: QualifiedName = stdLibName("joinToString")
}


object IfEmpty : FunctionApi {
   override val taxi: String = """
   [[ If the provided source array is empty, returns the value from the default ]]
   declare extension function <T> ifEmpty(source:T[], defaultValue:T[]): T[]
   """.trimIndent()

   override val name: QualifiedName = stdLibName("ifEmpty")
}

object OrEmpty : FunctionApi {
   override val taxi: String = """
   [[ If the provided source array is null, returns an empty collection ]]
   declare extension function <T> orEmpty(source:T[]): T[]
   """.trimIndent()

   override val name: QualifiedName = stdLibName("orEmpty")
}


object Append : FunctionApi {
   override val taxi: String = """
   [[ Returns a new collection, containing the elements of array1, then the elements of array2 ]]
   declare extension function <T> append(array1: T[], array2: T[]):T[]
   """
   override val name: QualifiedName = stdLibName("append")
}
