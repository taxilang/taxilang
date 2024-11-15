import {CodeSnippetMap} from "@/components/Guides/CodeSnippet";

export const publishYourApiCodeSnippets: CodeSnippetMap = {
  'taxi-simple': {
    name: 'src/types.taxi',
    lang: 'taxi',
    code: `   type PersonName inherits String
>  type CustomerId inherits String
   type Another inherits Date`
  },
  'open-api-example': {
    name: 'customer-api.oas.yaml',
    lang: 'yaml',
    code: `   # An extract of an OpenAPI spec:
   components:
     schemas:
       Customer:
         properties:
           id:
             type: string
>             # Embed semantic type metadata directly in OpenAPI
>             x-taxi-type:
>                name: CustomerId
             `
  },
  'taxi' : {
    name: 'customer.taxi',
    lang: 'taxi',
    code: `// shared types (defined in a shared project)
type FirstName inherits String
type CustomerId inherits Int
type LastName inherits String

model Customer {
  id : CustomerId
  firstName: FirstName
  lastName: LastName
  fullName = FirstName + ' ' + LastName
}

service CustomerApi {
  @HttpOperation(url = '/customers', method = 'GET')
  operation getCustomers():Customer[]

  operation getCustomer(CustomerId):Customer
}`
  },
  'protobuf-example': {
    name: 'customer-api.proto',
    lang: 'protobuf',
    code: `   import "org/taxilang/dataType.proto";

   message Customer {
      optional string customer_name = 1 [(taxi.dataType)="CustomerName"];
>     optional int32 customer_id = 2 [(taxi.dataType)="CustomerId"];
   }
    `
  },
  'database-example': {
    name: `database.taxi`,
    lang: 'taxi',
    code: `database CustomerDatabase {
   table customers : Customer
}

model Customer {
   id : CustomerId
   name : CustomerName
   ...
}
`
  }
};

export const microservicesCodeSnippets: CodeSnippetMap = {
  'taxi-simple': {
    name: 'types.taxi',
    lang: 'taxi',
    code: `// Define semantic types for the attributes
// that are shared between systems.
type MovieId inherits Int
type MovieTitle inherits String
type ReviewScore inherits Int
type AwardTitle inherits String
`
  },
  'taxi-query': {
    name: 'query.taxi',
    lang: 'taxi',
    code: `// Send a query for data to Orbital,
// and it builds the integration on demand,
// using metadata embedded in your API specs
find { Movies(ReleaseYear > 2018)[] }
as {
   // Consumers define the schema they want.
   // Orbital works out where to fetch data from
   title : MovieTitle // .. read from a db
   review : ReviewScore // .. call a REST API to find this
   awards : AwardTitle[] // ... and a gRPC service to find this.
}
        `
  },
  'open-api-example': {
    name: 'film-reviews-api.oas.yaml',
    lang: 'yaml',
    code: `   # An extract of an OpenAPI spec:
   components:
     schemas:
       Reviews:
         properties:
           id:
             type: string
>             # Embed semantic type metadata directly in OpenAPI
>             x-taxi-type:
>                name: MovieId
             `
  },
  'protobuf-example': {
    name: 'awards-service-api.proto',
    lang: 'protobuf',
    code: `   import "org/taxilang/dataType.proto";

   // rpc service that returns the awards movies have won
   service AwardsService {
      rpc GetMovieAwards(MovieAwardRequest) returns (MovieAwardResponse)
   }
   message MovieAwardRequest {
>      int32 movie_id = 1 [(taxi.dataType)="MovieId"];
   }
   message MovieAwardResponse {
       int32 movie_id = 1 [(taxi.dataType)="MovieId"];
>      repeated awards = 2 [(taxi.dataType)="AwardTitle[]"];
   }
    `
  },
  'database-example': {
    name: `database.taxi`,
    lang: 'taxi',
    code: `database FilmsDatabase {
   table films : Film[]
}

model Film {
   id : MovieId
   name : MovieTitle
   year : ReleaseYear
   ...
}
`
  },
  'csv-example': {
    name: 'ratings.taxi',
    lang: 'taxi',
    code: `@Csv
model FilmRatings {
   filmId : MovieId
   filmRating : FilmRating
 }`
  }
};
