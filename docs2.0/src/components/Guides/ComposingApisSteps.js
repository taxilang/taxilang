import {ClientSideCode, GuideStep, GuideSteps, TabbedSteps} from "@/components/Guides/GuideSteps";
import {TabBar} from "@/components/Guides/TabBar";
import {useRouter} from "next/router";
import {useEffect, useState} from "react";

const describeServicesSteps = [
  {
    framework: 'OpenAPI',
    steps: () => {
      return (<div>
        <p className='text-sm'>OpenAPI is pretty verbose. To keep things clear, we're just showing the relevant extracts here. The full OpenAPI spec is available on <a href='https://github.com/orbitalapi/demos/blob/main/composing-apis-and-db/services/api-docs.yaml' target='_blank'>Github</a></p>
        <ClientSideCode lang='yaml' name='OpenAPI.yml' code={`
paths:
   /reviews/{filmId}:
      get:
         parameters:
            -  name: filmId
               in: path
               schema:
                  type: string
                  x-taxi-type:
                     name: films.reviews.SquashedTomatoesFilmId
   /films/{filmId}/streamingProviders:
      get:
         parameters:
            -  name: filmId
               in: path
               schema:
                  type: integer
                  format: int32
                  x-taxi-type:
                     name: films.FilmId
components:
   schemas:
      StreamingProvider:
         type: object
         properties:
            name:
               type: string
               x-taxi-type:
                  name: films.StreamingProviderName
            pricePerMonth:
               type: number
               x-taxi-type:
                  name: films.StreamingProviderPrice
      FilmReview:
         type: object
         properties:
            filmId:
               type: string
               x-taxi-type:
                  name: films.reviews.SquashedTomatoesFilmId
            score:
               type: number
               x-taxi-type:
                  name: films.reviews.FilmReviewScore
            filmReview:
               type: string
               x-taxi-type:
                  name: films.reviews.ReviewText

`}>
      </ClientSideCode></div>)
    }
  },
  {
    framework: 'Taxi',
    steps: () => {
      return (<ClientSideCode lang='taxi' name='services.taxi' code={`
model StreamingProvider {
  name : StreamingProviderName
  pricePerMonth: StreamingProviderPrice
}

service StreamingProviderService {
  @HttpOperation(method = "GET", url = "/films/{filmId}/streamingProviders")
  operation findStreamingService(FilmId):StreamingProvider
}
`}>
      </ClientSideCode>);
    }
  },
  {
    framework: 'Spring Boot + Kotlin',
    slug: '#spring-boot-kotlin',
    steps: () => {
      return (<div>
          <p>We'll configure Spring Boot to auto-publish a schema. This is a great option for teams who like to generate specs from code.  </p>
        <p>This requires a little more up-front setup, but this is a one-time activity.</p>

<GuideSteps>
  <GuideStep stepNumber={1} title={'Update taxi.conf'}>
    <div>Update the <code>taxi.conf</code> file to add the Kotlin code generator</div>
    <ClientSideCode name={'taxi.conf'} lang={'hocon'} code={`plugins: {
   taxi/kotlin: {
      maven: {
         groupId: "com.petflix"
         artifactId: "films"
      }
   }
}`}>

    </ClientSideCode>
  </GuideStep>
  <GuideStep stepNumber={2} title={'Run the taxi build'}>
    <div>Run the taxi build which generates Kotlin classes for all our types, and outputs them in the <code>dist</code> folder</div>
    <ClientSideCode name={'Terminal'} lang={'terminal'} code={`taxi build`}>

    </ClientSideCode>
  </GuideStep>
  <GuideStep stepNumber={3} title={'Add the dependency to our maven pom'}>
    <div>Update the <code>pom.xml</code> file our newly created project</div>
    <ClientSideCode name={'pom.xml'} lang={'xml'} code={`&lt;dependency>
    &lt;groupId>com.petflix&lt;/groupId>
    &lt;artifact>films&lt;/artifact>
    &lt;version>0.1.0&lt;/version>
&lt;/dependency>    `}>

    </ClientSideCode>
  </GuideStep>
  <GuideStep stepNumber={4} title={'Add our type metadata to our response types'}>
    <div>Update our data classes to use the new semantic types created in step 2.</div>
    <ClientSideCode name={'pom.xml'} lang={'kotlin'} code={`
data class StreamingProvider(
    val name: StreamingProviderName,
    val pricePerMonth: StreamingProviderPrice
)
    `}>
    </ClientSideCode>
  </GuideStep>
  <GuideStep stepNumber={5} title={'Add our type metadata to our services'}>
    <div>Update our data classes to use the new semantic types created in step 2.</div>
    <ClientSideCode name={'pom.xml'} lang={'kotlin'} code={`@GetMapping("/films/{filmId}/streamingProviders")
fun whereCanIWatch(
  @PathVariable("filmId") filmId: FilmId
): StreamingProvider
    `}>
    </ClientSideCode>
  </GuideStep>
</GuideSteps>
        </div>

    );
    }
  },
]


export function DescribingServices() {
  return (<TabbedSteps stepData={describeServicesSteps}></TabbedSteps>);
}

const publishApisSteps = [
  {
    framework: 'OpenAPI',
    steps: () => {
      return (<div>
        <ClientSideCode lang='hocon' name='workspace.conf' code={`
file {
   projects = [
      {path: "taxi/taxi.conf"},
      {
         path: "services/api-docs.yaml",
         loader: {
            packageType: OpenApi
            identifier: {
               organisation: "com.petflix"
               name: "PetflixServices"
               version: "0.1.20"
            },
            defaultNamespace: "com.petflix"
         }
      }
   ]
}

`}>
        </ClientSideCode></div>)
    }
  },
  {
    framework: 'Taxi',
    steps: () => {
      return (<ClientSideCode lang='hocon' name='workspace.conf' code={`
file {
   projects = [
      {path: "taxi/taxi.conf"},
   ]
}

`}>
      </ClientSideCode>);
    }
  },
  {
    framework: 'Spring Boot + Kotlin',
    slug: '#spring-boot-kotlin',
    steps: () => {
      return (<div>
          <p>Our Spring boot services are now self-describing, we just need to publish them on startup</p>

          <GuideSteps>
            <GuideStep stepNumber={1} title={'Add maven dependency'}>
              <div>Update the <code>pom.xml</code> file to add the Kotlin code generator</div>
              <ClientSideCode name={'pom.xml'} lang={'xml'} code={`&lt;dependency>
    &lt;groupId>com.orbitalhq&lt;/groupId>
    &lt;artifact>schema-rsocket-publisher&lt;/artifact>
    &lt;version>\${orbital.version}&lt;/version>
&lt;/dependency> `}>

              </ClientSideCode>
            </GuideStep>
            <GuideStep stepNumber={2} title={'Generate and publish'} wide={true}>
              <div>Update our Spring Boot application to generate our schemas on startup and publish to Orbital</div>
              <ClientSideCode name={'App.kt'} lang={'kotlin'} code={`
@Component
class RegisterSchemaOnStartup(
    @Value("\${server.port}")
    private val serverPort: String,
    @Value("\${spring.application.name}")
    private val appName: String
) {
  init {
    val publisher = SchemaPublisherService(
        appName,
        RSocketSchemaPublisherTransport(
            TcpAddress("localhost", 7655)
        )
    )
    publisher.publish(
        PackageMetadata.from("io.petflix.demos", appName),
        SpringTaxiGenerator.forBaseUrl("http://localhost:\${serverPort}")
            .forPackage(StreamingMoviesProvider::class.java)
            .generate()
    ).subscribe()
  }
}
`}>

              </ClientSideCode>
            </GuideStep>
          </GuideSteps>
        </div>

      );
    }
  },
]



export function PublishingApiSpecsSteps() {
  return (<TabbedSteps stepData={publishApisSteps}></TabbedSteps>);
}
