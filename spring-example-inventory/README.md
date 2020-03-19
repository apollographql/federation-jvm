
To run the example, cd to the root of this project, then...
```
## compile and install project including example and dependencies
mvn install
## start local webserver on 9000
mvn -pl spring-example spring-boot:run
```
Now you can query your local graph:
```
## e.g.
curl -X POST  -H "Content-Type: application/json" --data '{"query":"{__schema{types{name}}}"}' "http://localhost:9000/graphql"
```