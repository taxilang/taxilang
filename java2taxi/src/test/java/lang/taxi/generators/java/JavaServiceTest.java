package lang.taxi.generators.java;

import lang.taxi.annotations.DataType;
import lang.taxi.annotations.Operation;
import lang.taxi.annotations.Service;

@Service("JavaService")
public class JavaServiceTest {

   @Operation
   public Person findByEmail(
      @DataType(value = "lang.taxi.FirstName", imported = true) String firstName
   ) {
      return null;
   }
}
