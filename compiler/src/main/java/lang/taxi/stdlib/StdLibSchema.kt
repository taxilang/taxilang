package lang.taxi.stdlib

import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.functions.stdlib.StdLib

object StdLibSchema  {
   val taxiDocument : TaxiDocument by lazy {
      Compiler(StdLib.taxi).compile()
   }
}
