package lang.taxi.generators.openApi.v3

import com.winterbe.expekt.should
import lang.taxi.compiled
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.types.PrimitiveType
import org.junit.jupiter.api.Test

class ServiceImportBug {

   // ORB-317
   @Test
   fun `path variables with names are decoded correctly`() {
      val openApiJsonSpec = """
{
  "openapi": "3.1.0",
  "info": {
    "title": "FastAPI",
    "version": "0.1.0"
  },
  "paths": {
    "/credit-score/{customer_id}": {
      "get": {
        "summary": "Credit Score",
        "operationId": "credit_score_credit_score__customer_id__get",
        "parameters": [
          {
            "name": "customer_id",
            "in": "path",
            "required": true,
            "schema": {
              "type": "string",
              "title": "Customer Id"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "Successful Response"
          }
        }
      }
    }
  }
}   """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiJsonSpec, "vyne.openApi")
      val compiled = taxiDef.taxi.compiled()
      compiled.service("vyne.openApi.Credit_scoreCustomer_idService")
         .operation("credit_score_credit_score__customer_id__get")
         .parameters.single().type.qualifiedName.should.equal(PrimitiveType.STRING.qualifiedName)
   }
}
