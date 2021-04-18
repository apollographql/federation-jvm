This Spring Boot application contains examples for both a microservice using standard `graphql-java` and a microservice using `graphql-java-tools`; their code is separated under different subpackages and Spring profiles.

To run the standard `graphql-java` example, `cd` to the root of this project, then...
```
## compile and install project including example and dependencies
mvn install -Dgpg.skip
## start local webserver on 9000
mvn -pl spring-example spring-boot:run
```
To run the `graphql-java-tools` example, for the last step instead run:
```
mvn -pl spring-example spring-boot:run -Dspring-boot.run.profiles=graphql-java-tools
```
Now you can query your local graph:
```
## e.g.
curl -X POST  -H "Content-Type: application/json" --data '{"query":"{__schema{types{name}}}"}' "http://localhost:9000/graphql"
```