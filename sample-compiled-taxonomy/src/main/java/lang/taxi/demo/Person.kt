package lang.taxi.demo

import lang.taxi.annotations.DataType

@DataType("lang.taxi.demo.FirstName", imported = true)
typealias FirstName = String

@DataType("lang.taxi.demo.LastName")
typealias LastName = String

// This is to test KAPT generation with imported = false
@DataType("lang.taxi.demo.NickName", imported = false)
typealias NickName = String

@DataType("lang.taxi.demo.Person")
data class Person(val first:FirstName, val lastName: LastName)
