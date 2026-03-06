package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(
      Contains,
      ContainsAll,
      ContainsAny,
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
      Append,
      Size,
      IsNullOrEmpty,
      CollectAllInstances,
      IndexOfItem

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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQwU7DMBBEf8XaS0HyF1jiUCSEygEOCC6Yg0k2jSFZB68DVFH+Hbtu3CLR2+6M7XnjCbhqsTegIOwGFBte170lYalFbwOLa+c6NKTpYN/UNjh/3n+2+I3/+b2rsRNPHM1JkxBjnMj0qPZamo6XHoO3tE2nbOZRC1jWMoQqOFnN0apAaJpBwueIfhfrbe0X0jE6x4qrrJzirG5H5JDclcxegWhMx1jEheKvulDs1STOkUNTY6k+VzypreF7t64qZFbLp0U6coQPzcWhvSyNZWl5mWsOxseXAnoGNc0SOIxvcXx5lYA/A1YB6zt2FD9i0rAgaFBCQ+mrQcb1hCT5wY+YAjiY6mNTg6Kx62Ced+/x1bzOv5F+1DdHAgAA
      DocsSnippet(
         markdown = "Returns `true` when all provided boolean conditions are `false`. Uses type-based access to combine semantic boolean types.",
         query = StubQueryMessage(
            schema = """
               type IsAdmin inherits Boolean
               type IsEditor inherits Boolean
               type IsViewer inherits Boolean
               model User {
                  username: Username inherits String
                  isAdmin: IsAdmin
                  isEditor: IsEditor
                  isViewer: IsViewer
               }
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
                  username: Username
                  hasNoAccess: Boolean = noneOf(IsAdmin, IsEditor, IsViewer)
               }
            """.trimIndent(),
            expectedJson = """{"username": "GuestUser", "hasNoAccess": true}"""
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
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WRwU7DMAyGX8XyZSBFPEAkDiAhUS4gIbhQDlnrroHUGUmKqKq-O2m6jY12N-fP_zv2lx59UVOjUGJjSzLw4slBnzMAsGpIwnNwmjdJqJW_a5Q2r-R0pQsVtGUJ9wsqaK7jMXi4tdaQ4n3-qbZMs_xMPZvPyln4VFpIDjmHbkuQ-clI5cwE6w4Ud4_VxdI2YnFGMX_8EgV-teS6iHOjv4l3JNsIVU5or3fSge_qxuiCVmKvLkOulPF07FkAGVx7YvnPKvVI90OCUmkuT756HPNqLJOmD7jkEbqYjEtulYu2QM6j7AeBPrTrWL69C6SfLRWBygdvOWLoc0wdUUKOadUcRSz_uo9X4-hjXx9U8ZmVKLk1Jj7j7EdsNh2HX2jEXTqsAgAA
      DocsSnippet(
         markdown = """Returns `true` when at least one of the provided boolean conditions is `true`. Define a computed type in the schema using `anyOf` for reuse across queries.""",
         query = StubQueryMessage(
            schema = """
               model User {
                  name: String
                  hasEmailVerification: HasEmailVerification inherits Boolean
                  hasPhoneVerification: HasPhoneVerification inherits Boolean
                  hasIdVerification: HasIdVerification inherits Boolean
               }
               type IsVerified inherits Boolean by anyOf(HasEmailVerification, HasPhoneVerification, HasIdVerification)
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
                  isVerified: IsVerified
               }
            """.trimIndent(),
            expectedJson = """{"name": "Alice", "isVerified": true}"""
         )
      )
   )
}

object None : FunctionApi, HasRunnableExamples {
   override val taxi: String =
      """
      [[
      Returns `true` if none of the items in the collection satisfy the predicate.

      Returns `true` for an empty collection (vacuous truth).

      ```taxi
      // Check that no task is overdue
      find { sprint: Sprint } as {
         noOverdue: Boolean = sprint.tasks.none((task) -> task.isOverdue)
      }
      ```
      ]]
      declare extension function <T> none(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("none")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42Qy07DMBBFf2U0m7ZS6AdYCovCpmzLLs7CxNMS6oyNH4gqyr9jJ5VQKxbs5nHn6N4ZMXTvNCgUOFhNBl5VOMMoGYDVQAIO0fd8Kn0fnuzgDMU83VlrSLHkCSv8TOQvGXDqv4iX25gpQcywpoUamjIEGK/Q1YFicqvqhhl9IpiqO+Uu9Ub/S/lMztjLn9KibLNZycee9TWeZdpzdx8pm53Nb8t+vS71kmMDD4/zavvLh7qGozKBNssnnPLZSyQfUIxThSGmt1w2bYX07aiLpF+C5fyrUeKtAYmL2YIJUXXnvUbByZhM9fYj3y7t9AN6UfPzsgEAAA==
      DocsSnippet(
         markdown = "Returns `true` when no item in the collection satisfies the predicate.",
         query = StubQueryMessage(
            schema = """
               model Task {
                  name: String
                  isComplete: Boolean
               }
            """.trimIndent(),
            query = """
               given {
                  tasks: Task[] = [
                     { name: 'Setup', isComplete: true },
                     { name: 'Build', isComplete: true },
                     { name: 'Deploy', isComplete: true }
                  ]
               }
               find {
                  noneIncomplete: Boolean = tasks.none((task: Task) -> task.isComplete == false)
               }
            """.trimIndent(),
            expectedJson = """{"noneIncomplete": true}"""
         )
      )
   )
}

object Any : FunctionApi, HasRunnableExamples {
   override val taxi: String =
      """
      [[
      Returns `true` if at least one item in the collection satisfies the predicate, or `false` if none do.

      Returns `false` for an empty collection.

      ```taxi
      // Check whether any order in a batch is flagged
      find { batch: OrderBatch } as {
         needsAttention: Boolean = batch.orders.any((order) -> order.isFlagged)
      }
      ```
      ]]
      declare extension function <T> any(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("any")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22Qz07DMAzGXyXyZZsU9gCR4DB2Yddxa3oIjTfKUqfkD6Kq-u54aaUJWE6O_fnnzx4hNu_YGVDQeYtOvJp4EaMmIQSZDpU4ptDSuSTa-Oy73mHi9M57h4Y0TSDhM2MYGHFuv5CW7sSgqAqvqsWjqEqW37iAV0dMuV_JX9gUMopJ_tPucuvsH-3JuHhXvMfe-eG-uohrtq3p1JJdzBoa9p5ua7Hf4n_LhfX6Gs6bbMTDU6lsb-jNfIPeBJ6eMERQ4yQhpvzGYVVLwO8em4T2ED3xlUYNyzwN88bX_phMc3mxoCg7x7jgP7hp_k4_isy3SacBAAA=
      DocsSnippet(
         markdown = "Returns `true` when at least one item satisfies the predicate.",
         query = StubQueryMessage(
            schema = """
               model Task {
                  name: String
                  isComplete: Boolean
               }
            """.trimIndent(),
            query = """
               given {
                  tasks: Task[] = [
                     { name: 'Setup', isComplete: true },
                     { name: 'Build', isComplete: false },
                     { name: 'Deploy', isComplete: false }
                  ]
               }
               find {
                  anyDone: Boolean = tasks.any((task: Task) -> task.isComplete)
               }
            """.trimIndent(),
            expectedJson = """{"anyDone": true}"""
         )
      )
   )
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
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22Rz07DMAzGXyXyZSBFPEAkDhsSYlxAmsSFcshSd8tIk5I_iKnqu-OUdltZb_Zn57P9SwtB7bGWIKB2JRr2kEJ0NXrWFpYxZmWNgm2i13bXCzosVdTfJK6HiGm7R69jYCvnDErb9-1l2KSq0kqjjY_JlkGwpytt_q0ObyRWGss8ZYxnervCxmOD2fjVY61TvVQKw7Ut2x6ZNOaluhm35jPb8Itpt8DhK6E_Epkd9dsBiBr4iDOp-6F0wrVYue2Cj9qZWPQJT_IcoEnDJYVc6PWuP7rStpx80LjVXU5H_hMk4goSOdGNjfT0JKIPINqOQ4hpS-H7Bwf8aVBFLJ-Ds0ShLaB3B8EKoAsL4BT8n5PLedvsHaJUn-sShE3G0CjvDmT4l3a_g_JgfnoCAAA=
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

object CollectAllInstances : FunctionApi, HasRunnableExamples {
   override val taxi: String =
      """
      [[
      Collects and merges all instances of a type from the current data context, bypassing ambiguity checks.

      Without this function, a search for `T[]` will return null when multiple separate collections of `T` exist
      (the result is considered ambiguous). `collectAllInstances` disables that check and concatenates all found instances
      into a single collection.

      ```taxi
      // Merge homeAddresses and workAddresses into one list
      find { person: Person } as {
         allAddresses: collectAllInstances(Address)
      }
      ```
      ]]
      declare extension function <T> collectAllInstances(collection: lang.taxi.Type<T>): T""".trimIndent()
   override val name: QualifiedName = stdLibName("collectAllInstances")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VQQU7DMBD8irWXgGTxAEscemwPgIBbnEOIt22oYxfbhVaR_87GTiIIJQdnvTPrmZ0efLPHrgYBnVWo2Uoph96zXhrGmA8OMQj2kv6sNXt0bfDDvTU7aaI0eewJnbdmnDJ1h4I90Pl3guC97XCUQS8mxbJK4Jd1h-tgBA4fJ3QX8rprP3FSOyZpMVm4H9uzj2LTdt2l4FN3IV9Offr6eeHi8by1TpHtgkV-nfKMOzQhUyZGNXMXi_wj82qXD6Qipmi3rVG_Is2r3g2X1K21_iHRWK2xCSut18aH2jTob0b4Nsd3rB2NBnoFRB85-HB6o7KsOOD5SLOoNiRAAfcSkgoIJiEFKIFT-VNxAEsi5mUydY5NQuRsAc6BXQNzEoRUg1Py3xzWCoQ5aU3GnX0ne_kavwHit1yVtQIAAA==
      DocsSnippet(
         markdown = "Merges all `Address` collections on a `Person` into a single flat list, even though they live in separate fields.",
         query = StubQueryMessage(
            schema = """
               model Address {
                  street: Street inherits String
               }
               model Person {
                  name: Name inherits String
                  homeAddresses: Address[]
                  workAddresses: Address[]
               }
            """.trimIndent(),
            query = """
               given {
                  person: Person = {
                     name: 'Jimmy',
                     homeAddresses: [
                        { street: 'Oxford St' },
                        { street: 'Regent St' }
                     ],
                     workAddresses: [
                        { street: 'Tot St' }
                     ]
                  }
               }
               find {
                  name: person.name
                  allAddresses: collectAllInstances(Address)
               }
            """.trimIndent(),
            expectedJson = """{"name": "Jimmy", "allAddresses": [{"street": "Oxford St"}, {"street": "Regent St"}, {"street": "Tot St"}]}"""
         )
      )
   )
}

object All : FunctionApi, HasRunnableExamples {
   override val taxi: String =
      """
      [[
      Returns `true` if every item in the collection satisfies the predicate, or `false` if any item does not.

      Returns `true` for an empty collection (vacuous truth).

      ```taxi
      // Check if every task in a sprint is complete
      find { sprint: Sprint } as {
         allDone: Boolean = sprint.tasks.all((task) -> task.isComplete)
      }
      ```
      ]]
      declare extension function <T> all(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("all")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42Qz07DMAzGXyXyaZPKHqASHMYucN1uTQ-hMSMsdUL-TJuivjteWmlCcCAnx_7882cXiMMHjgpaGJ1GKw4qnkSRJIQgNWIr9ikYOtaEic9u9BYTp7fOWVQkaYIGvjKGKyOO5oy0dCcGxbbyul48iq5m-ZUFLGGPKXsJzQ9wChnF1PxWb7Ox-t_qHXrrrn_Lq7pn65LeDenFsLJ25-i-GnuuO2y4sFrdwnmbtXh4qpXNnbye7-BV4PEJQ4S2TA3ElN847PoG8OJxSKhfoyO-VJGwzJMw27r1x6SG04uGlrK1jAvuk5vm7_QNbgqelqsBAAA=
      DocsSnippet(
         markdown = "Returns `true` when every item in the collection satisfies the predicate.",
         query = StubQueryMessage(
            schema = """
               model Task {
                  name: String
                  isComplete: Boolean
               }
            """.trimIndent(),
            query = """
               given {
                  tasks: Task[] = [
                     { name: 'Setup', isComplete: true },
                     { name: 'Build', isComplete: true },
                     { name: 'Deploy', isComplete: true }
                  ]
               }
               find {
                  allDone: Boolean = tasks.all((task: Task) -> task.isComplete)
               }
            """.trimIndent(),
            expectedJson = """{"allDone": true}"""
         )
      ),
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42Qz07DMAzGXyXyaZPKHqASO4xd4DpuTQ-h8UZY6oT8QZuivjteWmkCLuTk2J9__uwCcXjHUUELo9NaxauKZ1EkCSFIjdiKQwqGTjVh4pMbvcXE6Z1zFhVJmqCBz4zhyoiT-UJauhODYlt5XS8eRVez_MoClnDAlL2E5gc4hYxiav6qd9lY_W_1Hr1119_yo7KR9VXes3dJR0N6caws3Tu678am6xIbLqxWt3BeZy0etrWyuaPX8yG8Cjw_YYjQlqmBmPIbh13fAF48Dgn1S3TEpyoSlnkSFl83QExqOD9raClby7zgPrhr_k7fONhn3K0BAAA=
      DocsSnippet(
         markdown = "Returns `false` when at least one item does not satisfy the predicate.",
         query = StubQueryMessage(
            schema = """
               model Task {
                  name: String
                  isComplete: Boolean
               }
            """.trimIndent(),
            query = """
               given {
                  tasks: Task[] = [
                     { name: 'Setup', isComplete: true },
                     { name: 'Build', isComplete: true },
                     { name: 'Deploy', isComplete: false }
                  ]
               }
               find {
                  allDone: Boolean = tasks.all((task: Task) -> task.isComplete)
               }
            """.trimIndent(),
            expectedJson = """{"allDone": false}"""
         )
      )
   )
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/31Qy07DMBD8FWsvBiniAyxxKGpUFQmQ+jjVOZhk2xqSTbEdRBXl31k7KQIhuM3Ojmdm3YMvj9gYUBDOJxSrtkZh6YjOBi/WwVk6aGraCmux9ehEr0kI0TEk06BKZES/H7HMsZtXyXRXaBogg7cO3ZnTDvYd6Zvb6CRuJ+pHhpxVjaW4l9llO1nv5Hadr2QmZD5fbp4Sms0flo+ySMqBUzXtLVV/Nk+09SlDibuWjQ1xkbH1TdlSMJb81eR7PekXHfrwr36xzdebqI93n4zjrIDOg+qHDHzonhnuigzw44RlwOret8Q/02u4lNSghIav6zVkPE5V4y64DkcqtYnU3tQeY6APpnxdVqCoq2vOd+0Lp4zj8AlGjPm59wEAAA==
      DocsSnippet(
         markdown = "Checks whether a collection contains a specific element using type-based access.",
         query = StubQueryMessage(
            schema = """
               type Role inherits String
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
                  username: Username
                  isAdmin: Boolean = Role[].contains('ADMIN')
                  isGuest: Boolean = Role[].contains('GUEST')
               }
            """.trimIndent(),
            expectedJson = """{"username": "AdminUser", "isAdmin": true, "isGuest": false}"""
         )
      )
   )
}

object ContainsAll : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the collection contains every element in the search targets array.

      Returns `true` when `searchTargets` is empty (an empty set is a subset of every set).

      ```taxi
      // Check whether a product has all required tags
      find {
         hasCertified: Boolean = ProductTag[].containsAll(['certified', 'in-stock'])
      }
      ```

      **Known limitation:** When the collection uses a semantic type (e.g. `ProductTag inherits String`),
      passing string literals as `searchTargets` always returns `false` due to a type comparison bug.
      See `problems/containsAll.md`. Use a `String[]` collection as a workaround.
      ]]
      declare extension function <T> containsAll(collection: T[], searchTarget:T[]): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("containsAll")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/53QvU7EMAwH8FeJshSk6h4gEgO3gRiQjq3p4Et9bbnUDknKh6q+O26vfCwsbI4l__6xJ51chwNoowdu0KvHyM3osposKaUIBjTqkGNP7drI0KavRlVbmnWpX0aMHwK0_SvSNhgujPn2btS0acUDhMyhKDesKtCjy5Gpd0m6ReCY4ehxqd84notazZJk6dRTs_kdpD3n7mkV9swegSRji90t8s4xZegp3Xp_9XdIfb2CQ5-S7CTg_7wWBhlfteUmAaIsmzEmbaa51CmPRymrutT4HmQSm_vEJFebrP61jNVG5Thiqaz--dLSPYFPuNApgzvfNdrQ6L0kRX4W7_KcPwFxxwkE0AEAAA==
      // NOTE: This example uses raw String[] tags due to a known bug with semantic types.
      // See problems/containsAll.md for details.
      DocsSnippet(
         markdown = "Returns `true` when the collection contains all specified search values, `false` if any are missing.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
                  tags: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  product: Product = { name: 'Laptop', tags: ['electronics', 'portable', 'work'] }
               }
               find {
                  hasBothTags: Boolean = product.tags.containsAll(['electronics', 'portable'])
                  missingTag: Boolean = product.tags.containsAll(['electronics', 'gaming'])
               }
            """.trimIndent(),
            expectedJson = """{"hasBothTags": true, "missingTag": false}"""
         )
      )
   )
}

object ContainsAny : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the collection contains at least one of the specified search targets.

      Returns `true` when `searchTargets` is empty (symmetrical with `containsAll`).

      ```taxi
      // Check whether a product has any of a set of tags
      find {
         isRelevant: Boolean = ProductTag[].containsAny(['sale', 'featured', 'new'])
      }
      ```

      **Known limitation:** When the collection uses a semantic type (e.g. `ProductTag inherits String`),
      passing string literals as `searchTargets` always returns `false` due to a type comparison bug.
      See `problems/containsAny.md`. Use a `String[]` collection as a workaround.
      ]]
      declare extension function <T> containsAny(collection: T[], searchTarget:T[]): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("containsAny")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/53QvU7EMAwH8FeJshSk6h4gEgO3gRiQjq3p4Et9bbnUDknKh6q-O26vfCwsbI4l__6xJ51chwNoowdu0KvHyM3osposKaUIBjTqkGNP7drI0KavRlVbmnWpX0aMHwK0_SvSNhgujPn2btS0acUDhMyhKDesKtCjy5Gpd0m6ReCY4ehxqd84notazZJk6dRTs_kdpD3n7mkV9swegSRji90t8s4xZegp3Xp_9XdIfb2CQ5-S7CTg_7wWBhlfteUmAaIsmzEmbaa51CmPRymrutT4HmQSm_vEJFebrP61jNVG5Thiqaz--dLSPYFPuNApgzvfNdrQ6L0kRX4W7_KcPwFxxwkE0AEAAA==
      // NOTE: This example uses raw String[] tags due to a known bug with semantic types.
      // See problems/containsAny.md for details.
      DocsSnippet(
         markdown = "Returns `true` when the collection contains at least one of the search values, `false` when none match.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
                  tags: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  product: Product = { name: 'Laptop', tags: ['electronics', 'portable', 'work'] }
               }
               find {
                  hasAnyTag: Boolean = product.tags.containsAny(['gaming', 'portable'])
                  hasNone: Boolean = product.tags.containsAny(['gaming', 'sports'])
               }
            """.trimIndent(),
            expectedJson = """{"hasAnyTag": true, "hasNone": false}"""
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VRPW/DIBT8K+gtjiViqR0tpVK7uUuHKJPJQM1LQktwCrhKZPm/99k4dhq1E+/juDuOFnx1wKOEHI61QsPW6L2uLWuFZUyr/DooFNP2gE4Hz9bBabsfAP65Cvobc1aM1Yx6qWuD0grbCRu5Nx5dJG6osvJI9zZj9Re7j9J+MlFuiQ04fDXoLmR5T4J2ZoxsbBUntypJNNevEx6XM3kZB4y1w4OTcbN8SPjNA4NrkHX8H+zjL+xOGk/giN32RzfEsNNW3QfQV9m17VdyIJliv/2BVURfrWc7bQK6xWJEpGz5NH1EmuGZqMzlzeIizbSK0Z2kIx265SFvOw4+NO9UllsOeD5hFVC9+tpSuK2AyRbkTMCcoQBO/Z3RCJrCE9DL+SCrz0JBbhtjSN3VH6QR2+4H7Z+9330CAAA=
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/21RTW+CMBj+K00vaIIE3XagiR4WL25mMcGdxEOFd9oJhbVlmSH8971QPkz01L4fT5+PllRHZ0g5ZTTNYkjIVmVxERlShpIQyVNgXesDCyLkGZQwmgRGCXmql/SlYCR4/3w0y5WImhfwGOYriETKk1BWobSsa/kL0mTqanlzy6h77v0Bl6lDfwpQV9R6Erhvd0UHZTevLOzs9qW9bRBStrZGG56bLB851sFos5t63gzLVvRs7nmu55HKuUMGKVcmP2cSenSwRfR8QPu+7/r+I/COHxMwPXD3isCnAfiMwBdktbhDfVRNUl9CxtaW7ulXYLhIhpzQeJ+H23l3Nf5GAuNx2+i3J2S67BJyUQ1ZLHojExt4zhWqNqA0ZWXlUG2KI173B4fCXw6RgfhNZxK/pAzpna6QMlRMa+P1NaRDcCF1sEbSdtCw2mYTRN22GVa1Dm14dFnHlMkiSVCWyr6R3JbVP1HTh1TFAgAA=
      DocsSnippet(
         markdown = "Finds the single product matching the given SKU. Throws if no match or more than one match is found.",
         query = StubQueryMessage(
            schema = """
               model Product {
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
            expectedJson = """{"smartphoneDetails": {"name": "Smartphone", "sku": "SP-002", "price": 999.99}}"""
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WRMW+DMBCF/4p1C4nkRGm7WWqHbGmjKhJ0ChkccBMasKltqkbI/71nDDRRunQB8+7d83dHCyY7iooDg0rloiQbrfIms6RNJSGSV4KRV3ySQh6FLqwhsdWFPPiqOTWMxC9vtzWXypC2kl9CWqXPIa8O4YYN12x3aAYKn43QZ2Q4FOgP3mJoZRcpj6F2mbQNAiFtjxuteW1VHdEAGK2T2WJxFzl6Y4wrrm19VFKM5niD5vu/zAnfl8KOxmSJxoeIuGDc+ZfrJn8vZB4wyw6E/Y4yH7DnBvdUiuV5Muk3MSWzJ79MOgITbrww9Um2u/zfSQPkmOR3XXOME1mhDbDWUTC22eNxu6MgvmuRWZE/GyXxb7QphBFSYDgR+E34YwrrXqZ4xn30YsedgvNqIL5pTHr5qjFgYqPHM5Znp1UOTDZlibRafSBT+HQ/iqkab68CAAA=
      DocsSnippet(
         markdown = "Looks up products by SKU using a cached index. Faster than `single()` when querying the same collection multiple times.",
         query = StubQueryMessage(
            schema = """
               model Product {
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
                        { name: 'Laptop', sku: 'LT-001' },
                        { name: 'Smartphone', sku: 'SP-002' },
                        { name: 'Tablet', sku: 'TB-003' }
                     ]
                  }
               }
               find {
                  laptop: inventory.products.singleBy((Product) -> SKU, 'LT-001' as SKU)
                  tablet: inventory.products.singleBy((Product) -> SKU, 'TB-003' as SKU)
               }
            """.trimIndent(),
            expectedJson = """{"laptop": {"name": "Laptop", "sku": "LT-001"}, "tablet": {"name": "Tablet", "sku": "TB-003"}}"""
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/31RQU7DMBD8iuVLQHKjNG0kGolDVSQEB5CAW9OD62xbQ2IH20GgKH/HrtsmgcIp2fXO7OxMgzXbQUlxikuZQ4FeFBWaMsOlQE0mEMqpgRQ9G8XF1tW0lLUwKboBxkta7EdAM8Urh+km20x4xjljDuHZqC8e6nINqk9rur067atYriwXJvi9BvVlZW75B4gBWXpace37v9YE88ViNI4n0yQgfmC4bumbCDWHc4M4iiejKBlF44CcTh4nURhFZHhvcKsks9LsLVJBgFryB9l0FF/1yKZJOJv95HoUBReAnney+p8p6THF51TtHdyAsjSeZeU+7T6XDRf52TgOZThou7lSavMEDITpJTOIyXp/RPe9DTdcaXNx6TOsqKIlGFAap01LsDb12v4uVwTDZwXMQH6vpbApNxkeisApyvApxgwTW55V5SYt2hnmQV2UHuVtc2/HPO10550HDULNcOvUa0PZ212OU1EXhT1GyVcr2ZftNyPMIUdJAwAA=
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/61SQU+DMBj9byktaIsQxkbimjgsS2P04A7zNnboSreh0GJbMob/3pZuDNzUiyd4H9979r03TuPuqUIOHOqMh-DZxaWiJMMUcCzKhkFRGgya2jLAjZ3Y8VHEiNdHWY5RMi7RoHpjjFKJpb1W1JGZhMUb5MwLaH8U7VkZt5/yBX5V3dCQF8FFMV2EZXfCODXVknFdvTSmW3bPdqGVy70a/ZiJn7QJcB/9K/dUAOJ0mPZeaVWJEMqWS3Jvop12/jvVjF40NpDWTJp4U+Dj6kTgYOIoHf3cMGS9j/7f+Q3lZ96Y0GXyf1hM5RIf8yEXtcvLT3ahNHKgWRCgXfOdv0VafNpLhSyy4P0TZZ0/N0c0OgRY3HAbBOPyWQ0FVk4WP4yDTjQ7HrqWnXlSVHNOvFSZYp1h65BomF8HzQLrqygKbR3RlbKrdEd2aTlCe-F1D6Mc1/X24MWN6u55d1-JgjxaMpMNFiLWlcw=
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

object Size : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the number of elements in the collection.

      ```taxi
      find { orders: Order[] } as {
         orderCount: Int = orders.size()
      }
      ```
      ]]
      declare extension function <T> size(collection:T[]):Int
      """.trimIndent()
   override val name: QualifiedName = stdLibName("size")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/0WOsQ6CQAyGX2UyF00ILm6XODnxwPHIcJyep1CwdxjVi+/ukqgpbd-mb_63ga9O1Bks2PU1tbA3LkDUDGADdV7AMTjLTV5onjDF20juKcf13olXu0oItXA7iF8sT8wwtJSkkJSGZeYkVc49kwImsWk-Wa7_Xft-ZBFlPHtm6eaj2ni7otV6qR-MMx0Fch5VnFL0YSwl5kWK9BioClT__s_yYNT4k2pUsJ1xH0x1zWpUPLat2Fx_EWZZ1DdvDlseCwEAAA==
      DocsSnippet(
         markdown = "Returns the number of elements in the collection.",
         query = StubQueryMessage(
            schema = """
               model Cart {
                  items: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  cart: Cart = { items: ['apple', 'banana', 'cherry'] }
               }
               find {
                  itemCount: Int = cart.items.size()
               }
            """.trimIndent(),
            expectedJson = """{"itemCount": 3}"""
         )
      )
   )
}

object IsNullOrEmpty : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the provided collection is either `null` or contains zero elements.

      ```taxi
      find { order: Order } as {
         hasNoItems: Boolean = order.items.isNullOrEmpty()
      }
      ```
      ]]
      declare extension function <T> isNullOrEmpty(collection:T[]):Boolean
      """.trimIndent()
   override val name: QualifiedName = stdLibName("isNullOrEmpty")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3WQQU7EIBCF-yrNhqTZ-AFS2qUrXbiwWxuGpiooa8G6l-TuvfBqXDoRM7zzH97P5xq29IlagwLTFijYu54rUNQMYAJ1XsAxOOsqX3iesIXbSO4px429Ey93lRBq4XYQv1iemGFoKUkhKQ3LzEmqnHsmBUxi03yyXP-79v3IIsp49szSzUe18XZFq_VSPxhnOgrkPKo4pejDWErMixTpMVAVqP79n-XBqPEnVatgO-M-mOqa1ah4bFuxuf4izLKoa94cth9aMq0ZrwEAAA==
      DocsSnippet(
         markdown = "Returns `true` for an empty collection, `false` when the collection has elements.",
         query = StubQueryMessage(
            schema = """
               model Bag {
                  items: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  emptyBag: Bag = { items: [] },
                  fullBag: Bag = { items: ['apple', 'banana'] }
               }
               find {
                  emptyCheck: Boolean = emptyBag.items.isNullOrEmpty()
                  fullCheck: Boolean = fullBag.items.isNullOrEmpty()
               }
            """.trimIndent(),
            expectedJson = """{"emptyCheck": true, "fullCheck": false}"""
         )
      )
   )
}

// Named to disambiguate between String.IndexOf and Array.IndexOf
object IndexOfItem : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the zero-based index of an item within a collection, or `-1` if the item was not found.

      ```taxi
      find { playlist: Playlist } as {
         songPosition: Int = playlist.tracks.indexOfItem('Bohemian Rhapsody')
      }
      ```
      ]]
      declare extension function <T> indexOfItem(collection:T[], searchItem: T):Int
      """.trimIndent()
   override val name: QualifiedName = stdLibName("indexOfItem")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42PwU4DIRCGX2Uyl2pCfAASL9706t5KD7NlpKxAK9BV0/TdhZJNTDyYEBgm3z/z/xumcWJPKNHPmh2cyMCmAsCV3MoSXnO0waiwo8CPleN3IY29cmhUJpNuUD/AI/QduWWiTkB35ny8hrynbigzVHizQTflZbaB9U1blHXUQ+2e5ta7q+L7yn7aPD1FGt85p38V5fT1Gqq22l4okufMMaHcdoEpr+dS9oNA/lp4zKxf0hxKsE1hs6VQgsIjiYCaQ8CRQqEo/d92Gtn/RQeFdXnKhXvWKMPqXPES50vZ2L77D+/rFg5+AQAA
      DocsSnippet(
         markdown = "Returns the index of an item in the collection, or `-1` if not found.",
         query = StubQueryMessage(
            schema = """
               model Playlist {
                  tracks: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  playlist: Playlist = { tracks: ['Alpha', 'Beta', 'Gamma'] }
               }
               find {
                  betaIndex: Int = playlist.tracks.indexOfItem('Beta')
                  missingIndex: Int = playlist.tracks.indexOfItem('Delta')
               }
            """.trimIndent(),
            expectedJson = """{"betaIndex": 1, "missingIndex": -1}"""
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4WRTU/jQAyG/4o1l4AUIT60QorEAQRoi1iEKLemhyFx02FTJzvj7LaK8t/Xk2lpqlTiNDO257H9vq1y2RJXWiVqVeVYwrSiAtqUANhwiQm8+wMMLdEadjBla6jweW3ZOE7gtj+PVeSN1WwqSuB+e9tXTYhT6lIKXV9LvSk9pe9MeiWNd7EXeR2jO5nUJf3As7mwVKz+NGg3skph/iIFVr2l7HlwEzK7PtFbpXN4t6aGX2YdxSG3pc/CC6Dd6RH9NMXyn97IJNpG8ZcM0T1iDa+NrUuU8H73q+tL6OIx564yBFzBW0NDzJ1tMoRp7dd0jEgHsMvr86OwZ7PwIgEvER61LPmsCYfYB12U6A5ZPy6gC6i5P7rekIWh/FC6l5EdPrsw1rEXP1ggqgYnzgrkW55QjuuT89PeKMwqyr8pvTgNFtbaSgNG61TSdrFy3HzIdTaPFa5rzBjzJ/FGTG5TNZwwVQmk6sDLVMUS+hrUV8inXrJQPbQyFAe9QnZgaEjupPNpcbXzsf1yY/zA4TF95PO4h5jdeU0c6+z3JFcJNWUpEtnqU4QIz+4/kJ88RcIDAAA=
      DocsSnippet(
         markdown = "Retrieves a song from a playlist by its zero-based index position.",
         query = StubQueryMessage(
            schema = """
               model Song {
                  title: Title inherits String
                  artist: Artist inherits String
                  duration: Duration inherits Int
               }
               model Playlist {
                  name: PlaylistName inherits String
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
                  playlistName: PlaylistName
                  firstSong: Song = Song[].getAtIndex(0)
                  secondSong: Song = Song[].getAtIndex(1)
               }
            """.trimIndent(),
            expectedJson = """{
               "playlistName": "Road Trip Mix",
               "firstSong": {"title": "Highway Star", "artist": "Deep Purple", "duration": 372},
               "secondSong": {"title": "Born to Run", "artist": "Bruce Springsteen", "duration": 270}
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WQQUvDQBCF/8owl7YQpShSCVqoh0J78OIxm0OaTNLVZFJ3N2IJ+e/Opgm01ouXZZfv7Zs3r0Wb7qlKMMSqzqiEtdHEGbSKATipKIRXOUHznox2Ft6c0Vx4mhQCV8UZ27BT3GGAnw2Zo1gW+ov45JX3vjYcBkQxPEPkAUA7DJpsdTUJTr53c+iC3zhJP0b++AdflTqlUbB4uBa81LsR38t/T2MJrDjX48qWWNdmPYb1u/dRh/i3uS4dmelU9p7BzbLffwmL+QwSO6i9zbFuuPify5NkunDxRR4SIy/RWgzbLkDrmp1cozhA+j5Q6ijb2pql6lbhRXaFofSLvjOFASjs21EY+/t5vFGoK4F+pnXyZ5NhyE1ZSgRTv8ug07P7AUOorjkwAgAA=
      DocsSnippet(
         markdown = "Filters a collection by predicate, returning only matching elements. Uses type-based lambda for clean, semantic access.",
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
                  seniorFriends: Name[] = friends.filter((Age) -> Age > 70) as Name[]
                  // Names of young adults (under 30)
                  youngFriends: Name[] = friends.filter((Age) -> Age < 30) as Name[]
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/31RvU7DMBB+Jep2MokeINIBdWAoqp0YgztJAktcB9sBVKl8d+w6TRLVJ9/H9773vjewmW/QBXLKF4HE97YcwUcXBkHioiKcqQT7GvGE8fTNgP9ZsJfVGBR3xNs4fVa7t1N8mK2HWpxSijv5cpUVRt7LgHvE2Q/8yc8mSdMaXHkxOuXHYVqSGsq9c4z1eUbF/Uo8z2wX6GbIUG91txU5GJRoM7BFHQV8xI4Kxb77fLFBYkXLc48g6Ev0KkVlE36oLzF2eJSTFr5hR9gR/0R55I56Iez8X/TXO6OQ3PrIRzMcvGHHM9dxLPFz+3YEJauE0vJr0bT5Vo0HJFzAk/saTzpO/MXqbpgZRFiPLHqKF3u9/8hBbLoLvqfM/9J8IQJKL/+gZ63M/sME9snjjl2HMSz9LvLeBfWwj6AZAAA=
      DocsSnippet(
         markdown = "Returns only elements present in both collections, identified by value equality.",
         query = StubQueryMessage(
            schema = """
               type Tag inherits String
               model Product {
                  name: ProductName inherits String
                  tags: Tag[]
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
            expectedJson = """{"productAName": "Smartphone", "productBName": "Digital Camera", "commonTags": ["electronics", "camera"]}"""
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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/81STWuDQBD9K8NeTGAbaHITUiiJtJYQQ4Re1IN113Rbs6buGlrE/95Z16QppvdeZD7evHlvnYao7JXvU+KSfcl4AUHFeAVNLAEEcyHUlZA7kymd6lr9VFpCyUfNqy8c3Ykjl3aoNPMI63iiBOYQmTJA0/E5Xf/m1qFnQmfjrZf++sGBll6BTi+h4aO/2XjLP6CzS+jifr3wVh3WQBNUHMtcSGZ1HtNCsGAg1sqf5KLQvBqNutTt2mO4ubM7C6F0kI/Ouila2AYLLwz77KQSw6W38p+9LSbjSVZKnQqpLOvEKh0bzu4j5L/RBPM55GmheC/O/OtDWqV7jgoUcZuWEqXrFwyjhBL+eeCZ5uxJlRKvoYnJhZOYuBBhSTATxaQ/gJhQTOw+2+ilx6SlMMBPh/jeEeIT0/r1fNeXzoYkpysxLMYl9rJ3nxFX1kWBpqvyDa3ZtP0GfnOcnS0DAAA=
      DocsSnippet(
         markdown = "Creates an inline collection to check if an order status is one of the valid values.",
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
                  invalidOrders: Order[] = orders.filter((order:Order) ->
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

object JoinToString : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
   [[
   Creates a string from all the elements separated using `separator` and using the given `prefix` and `postfix` if supplied. Null values are omitted.

   ```taxi
   find { order: Order } as {
      tagList: String = order.tags.joinToString(', ')
   }
   ```
   ]]
   declare extension function <T> joinToString(values:T[], separator: String = ",", prefix: String? = null, postfix: String? = null): String
   """.trimIndent()

   override val name: QualifiedName = stdLibName("joinToString")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4WPwU7EIBCGX2Uyl2pCfAASL9706t5KD7NlpKxAK9BV0/TdhZJNTDyYEBgm3z/z/xumcWJPKNHPmh2cyMCmAsCV3MoSXnO0waiwo8CPleN3IY29cmhUJpNuUD/AI/QduWWiTkB35ny8hrynbigzVHizQTflZbaB9U1blHXUQ+2e5ta7q+L7yn7aPD1FGt85p38V5fT1Gqq22l4okufMMaHcdoEpr+dS9oNA/lp4zKxf0hxKsE1hs6VQgsIjiYCaQ8CRQqEo/d92Gtn/RQeFdXnKhXvWKMPqXPES50vZ2L77D+/rFg5+AQAA
      DocsSnippet(
         markdown = "Joins array elements into a single string, with optional separator, prefix, and postfix.",
         query = StubQueryMessage(
            schema = "",
            query = """
               given {
                  tags: String[] = ['alpha', 'beta', 'gamma']
               }
               find {
                  joined: String = tags.joinToString(', ')
                  withBrackets: String = tags.joinToString(', ', '[', ']')
               }
            """.trimIndent(),
            expectedJson = """{"joined": "alpha, beta, gamma", "withBrackets": "[alpha, beta, gamma]"}"""
         )
      )
   )
}


object IfEmpty : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
   [[
   Returns the source array if it is non-empty, otherwise returns the provided default array.

   ```taxi
   find { order: Order } as {
      tags: order.tags.ifEmpty(['untagged'])
   }
   ```
   ]]
   declare extension function <T> ifEmpty(source:T[], defaultValue:T[]): T[]
   """.trimIndent()

   override val name: QualifiedName = stdLibName("ifEmpty")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/32QwU7FIBBFf2UyGzQh7wNIXLrQjQvdFRZYphVtaQVqfCH9dwdf+9QY3QFzz9x7KZjaJxotKhwnRwPcRUcRig4A2fZJwX2OPvSN0WFFia8LxSOLe/9G4SSjcc7HT0xt9BWUDW4MrLKKumUY/tIIep8jpSQkiN53WTDEbjp0PrhvHg8/8vCGL+dDXXXw3XV9uWhEyjY4G50wl7v7L/oc6V+4lp5ttCNliglVWSWmvDzysTESOTq1mdxtmgJ/S9F4jqqRu2nct2k0EjTuUbbpVl1jndXyLKuWTLUvNw5VYD0niNMz+5yu6wdHuqi4tQEAAA==
      DocsSnippet(
         markdown = "Returns the default array when the source is empty, or the source array when it has elements.",
         query = StubQueryMessage(
            schema = """
               model Order {
                  tags: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  emptyOrder: Order = { tags: [] },
                  fullOrder: Order = { tags: ['express', 'gift'] }
               }
               find {
                  emptyTags: String[] = emptyOrder.tags.ifEmpty(['standard'])
                  fullTags: String[] = fullOrder.tags.ifEmpty(['standard'])
               }
            """.trimIndent(),
            expectedJson = """{"emptyTags": ["standard"], "fullTags": ["express", "gift"]}"""
         )
      )
   )
}

object OrEmpty : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
   [[
   Returns the source array unchanged if it is non-null, or an empty array if it is `null`.

   Useful for safely chaining collection operations on potentially-null fields.

   ```taxi
   find { user: User } as {
      roles: user.roles.orEmpty().joinToString(', ')
   }
   ```
   ]]
   declare extension function <T> orEmpty(source:T[]): T[]
   """.trimIndent()

   override val name: QualifiedName = stdLibName("orEmpty")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22OsW7DMAxEf4Xg4hQQ8gECMgUd2rWj6UG22dStRSuSHCQQ/O9lLHQruJDE3bsrmIYv9g4t+mXkGc4uZigkAFNmnyx85DjJpe1INjR4XTk+VHyZbixVN6jDVt8Jyp+tbVwIMzcGmt6JTtPBpgySz0nGfxPU/mQd9/dxia8+5MfhpQYHF53nzDGhLZvBlNde17YzyPfAQ+bxPS2i1QrhTiDUFoR7DUIDhLUIYfcEpuyGn7cRrazzrPy4fCulntsvw2UknRcBAAA=
      DocsSnippet(
         markdown = "Returns the collection unchanged when non-null. Use `.orEmpty()` to safely chain operations on nullable arrays.",
         query = StubQueryMessage(
            schema = """
               model Cart {
                  items: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  cart: Cart = { items: ['apple', 'banana'] }
               }
               find {
                  items: String[] = cart.items.orEmpty()
               }
            """.trimIndent(),
            expectedJson = """{"items": ["apple", "banana"]}"""
         )
      )
   )
}


object Append : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
   [[
   Returns a new collection containing the elements of the first array followed by the elements of the second array.

   Neither input array is modified.

   ```taxi
   find { cart: ShoppingCart } as {
      allItems: cart.regularItems.append(cart.saleItems)
   }
   ```
   ]]
   declare extension function <T> append(array1: T[], array2: T[]):T[]
   """.trimIndent()
   override val name: QualifiedName = stdLibName("append")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22PsU7EMBBEf8XaJodknQSlJRo6aCnjFE68BEO8ydnOiZOVf2cdX0SD3IxXs_NmM8ThE70BBX62OIkXM4qsSQjhEvqoxHsKjsa207SBhMuK4cbm0V2R7sbejI9qX3wW-VhrG7MsEzZSNL0hfk0nNnn4n_7xc48QbmXBmoTFzkhNH47sHTTMvneE9q8UJxT6eQ85MxHJnkp-nTzU0osJxmPCEEHlTUJMa8-y7STgz4JDQvsWZ-KzsoYDooFLadiv0CCFhnpH1bVs1aWuhq6QYjLD96sFRes0MTjMXxxfv9svwAJvImwBAAA=
      DocsSnippet(
         markdown = "Concatenates two arrays, returning a new array with elements of the first followed by elements of the second.",
         query = StubQueryMessage(
            schema = """
               model Bag {
                  items: String[]
               }
            """.trimIndent(),
            query = """
               given {
                  bag1: Bag = { items: ['apple', 'banana'] },
                  bag2: Bag = { items: ['cherry', 'date'] }
               }
               find {
                  combined: String[] = bag1.items.append(bag2.items)
               }
            """.trimIndent(),
            expectedJson = """{"combined": ["apple", "banana", "cherry", "date"]}"""
         )
      )
   )
}
