# Type Alias Processor
Kotlin's compiler doesn't currently retain any metadata about type aliases.

This is an annotation processor that which will find kotlin `typealias` types 
which are annotated with an annotation, and export static metadata about them.

## Usage

Annotate your typealias types with `@DataType`:

```kotlin
@DataType
typealias FirstName = String

```

Add the following to your pom:

```xml
 <build>
  <plugins>
     <plugin>
        <artifactId>kotlin-maven-plugin</artifactId>
        <groupId>org.jetbrains.kotlin</groupId>
        <version>${kotlin.version}</version>
        <executions>
           <execution>
              <id>kapt</id>
              <goals>
                 <goal>kapt</goal>
              </goals>
              <configuration>
                 <annotationProcessorPaths>
                    <annotationProcessorPath>
                       <groupId>lang.taxi</groupId>
                       <artifactId>taxi-annotation-processor</artifactId>
                       <version>0.1.0-SNAPSHOT</version>
                    </annotationProcessorPath>
                 </annotationProcessorPaths>
              </configuration>
           </execution>
        </executions>
     </plugin>
  </plugins>
</build>
```

Then run `mvn install` to generate.

A `TypeAliases` class will be generated for each package that contains exported type aliases.
These then need to be registered with a registry on startup:

```kotlin
TypeAliasRegistry.register(TypeAliases::class)
```

The registry can then be queried for alias data.

## Troubleshooting

### No type aliases compiled
Projects need at least one non typealiased type to be compiled, or all type alias data
is lost before the annotation processor has a chance to operate.

eg:

```kotlin
// Won't work (on it's own):

@DataType
typealias FirstName = String

// Adding a compiled type will cause the typealias to get compiled
@DataType
data class Car(type:String) 
```

Note - the concrete type doesn't need to reference the type alias, but it *must* contain the annotation, in order
for the annotation processor to get initiatied.


## Developer notes
Debugging KAPT can be tricky.
Here's how to do it (from IntelliJ)

 * `mvn install` this project
 * In the project that is using the kapt plugin, set the dependency version to the snapshot just installed
 * In IntelliJ, naviagte to Maven -> The project depending on this plugin -> plugins -> kotlin -> kotlin:kapt -> right click -> debug
 
 Note - on change, you have to mvn install again. 
