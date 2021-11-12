package com.apollographql.federation.graphqljava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apollographql.federation.graphqljava.caching.CacheControlInstrumentation;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

public class CacheControlInstrumentationTest {
  private static final String DIRECTIVE_DEF =
      "enum CacheControlScope { PUBLIC PRIVATE }\n"
          + "directive @cacheControl(maxAge: Int scope: CacheControlScope inheritMaxAge: Boolean) on FIELD_DEFINITION | OBJECT | INTERFACE | UNION\n";

  static GraphQL makeExecutor(String sdl, int defaultMaxAge) {
    TypeDefinitionRegistry typeDefs = new SchemaParser().parse(DIRECTIVE_DEF + sdl);

    RuntimeWiring resolvers =
        RuntimeWiring.newRuntimeWiring().wiringFactory(new WiringFactoryImpl()).build();

    GraphQLSchema schema =
        Federation.transform(typeDefs, resolvers)
            .fetchEntities(env -> env.<List<Map<String, Object>>>getArgument(_Entity.argumentName))
            .resolveEntityType(env -> env.getSchema().getObjectType("Product"))
            .build();

    return GraphQL.newGraphQL(schema)
        .instrumentation(new CacheControlInstrumentation(defaultMaxAge))
        .build();
  }

  static @Nullable String execute(String schema, String query) {
    return execute(schema, query, 0, new HashMap<>());
  }

  static @Nullable String execute(String schema, String query, int defaultMaxAge) {
    return execute(schema, query, defaultMaxAge, new HashMap<>());
  }

  static @Nullable String execute(
      String schema, String query, int defaultMaxAge, Map<String, Object> variables) {
    GraphQL graphql = makeExecutor(schema, defaultMaxAge);

    ExecutionInput input =
        ExecutionInput.newExecutionInput().query(query).variables(variables).build();
    Map<String, Object> result = graphql.execute(input).toSpecification();

    assertNull(result.get("errors"), "response contains errors");

    GraphQLContext context = input.getGraphQLContext();
    return CacheControlInstrumentation.cacheControlHeaderFromGraphQLContext(context);
  }

  static class WiringFactoryImpl implements WiringFactory {
    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment environment) {
      return true;
    }

    @Override
    public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {
      if (environment.getFieldType() instanceof GraphQLList) {
        return (env) -> {
          ArrayList<Object> objects = new ArrayList<>();
          objects.add(new Object());
          return objects;
        };
      }
      if (environment.getFieldType() instanceof GraphQLObjectType) {
        return (env) -> new Object();
      }
      return (env) -> "hello";
    }
  }

  @Test
  void noHints() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid"
            + "}"
            + ""
            + "type Droid {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertNull(execute(schema, query));
  }

  @Test
  void noHintsTopLevel() {
    String schema = "type Query { name: String }";
    String query = "{ name }";
    assertNull(execute(schema, query));
  }

  @Test
  void defaultMaxAge() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid"
            + "}"
            + ""
            + "type Droid {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=10, public", execute(schema, query, 10));
  }

  @Test
  void hintOnField() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid @cacheControl(maxAge: 60)"
            + "}"
            + ""
            + "type Droid {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=60, public", execute(schema, query));
  }

  @Test
  void hintOnReturnType() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 60) {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=60, public", execute(schema, query));
  }

  @Test
  void hintOnReturnTypeList() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): [Droid]"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 60) {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=60, public", execute(schema, query));
  }

  @Test
  void hintOnReturnTypeExtension() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid"
            + "}"
            + ""
            + "type Droid {"
            + "  id: ID!"
            + "  name: String"
            + "}"
            + ""
            + "extend type Droid @cacheControl(maxAge: 60)";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=60, public", execute(schema, query));
  }

  @Test
  void overrideDefaultMaxAge() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 0) {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertNull(execute(schema, query, 10));
  }

  @Test
  void hintOnFieldOverrideMaxAgeHintOnReturnType() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid @cacheControl(maxAge: 120)"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 60) {"
            + "  id: ID!"
            + "  name: String!"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=120, public", execute(schema, query));
  }

  @Test
  void scopeHintOnReturnType() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid @cacheControl(maxAge: 120)"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 60, scope: PRIVATE) {"
            + "  id: ID!"
            + "  name: String!"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=120, private", execute(schema, query));
  }

  @Test
  void privateOnFieldOverridesPublicOnType() {
    String schema =
        "type Query {"
            + "  droid(id: ID!): Droid @cacheControl(scope: PRIVATE)"
            + "}"
            + ""
            + "type Droid @cacheControl(maxAge: 60, scope: PUBLIC) {"
            + "  id: ID!"
            + "  name: String!"
            + "}";
    String query = "{ droid(id: 2001) { name } }";
    assertEquals("max-age=60, private", execute(schema, query));
  }

  @Test
  void inheritMaxAge() {
    String schema =
        "type Query {\n"
            + "  topLevel: DroidQuery @cacheControl(maxAge: 1000)\n"
            + "}\n"
            + "\n"
            + "type DroidQuery {\n"
            + "  droid: Droid @cacheControl(inheritMaxAge: true)\n"
            + "  droids: [Droid] @cacheControl(inheritMaxAge: true)\n"
            + "}\n"
            + "\n"
            + "type Droid {\n"
            + "  uncachedField: Droid\n"
            + "  scalarField: String\n"
            + "  cachedField: String @cacheControl(maxAge: 30)\n"
            + "}";
    String query = "{ topLevel { droid { cachedField } } }";
    assertEquals("max-age=30, public", execute(schema, query));

    String query2 = "{ topLevel { droid { uncachedField { cachedField } cachedField } } }";
    assertNull(execute(schema, query2));

    String query3 = "{ topLevel { droids { uncachedField { cachedField } cachedField } } }";
    assertNull(execute(schema, query3));
  }

  @Test
  void inheritMaxAgeDocsExamples() {
    String schema =
        "type Query {\n"
            + "  book: Book\n"
            + "  cachedBook: Book @cacheControl(maxAge: 60)\n"
            + "  reader: Reader @cacheControl(maxAge: 40)\n"
            + "}\n"
            + "type Book {\n"
            + "  title: String\n"
            + "  cachedTitle: String @cacheControl(maxAge: 30)\n"
            + "}\n"
            + "type Reader {\n"
            + "  book: Book @cacheControl(inheritMaxAge: true)\n"
            + "}";

    assertNull(execute(schema, "{book{cachedTitle}}"));
    assertEquals("max-age=60, public", execute(schema, "{cachedBook{title}}"));
    assertEquals("max-age=30, public", execute(schema, "{cachedBook{cachedTitle}}"));
    assertEquals("max-age=40, public", execute(schema, "{reader{book{title}}}"));
  }

  @Test
  void inheritMaxAgeWithScope() {
    String schema =
        "type Query {\n"
            + "  topLevel: TopLevel @cacheControl(maxAge: 500)\n"
            + "}\n"
            + "type TopLevel {\n"
            + "  foo: Foo @cacheControl(inheritMaxAge: true, scope: PRIVATE)\n"
            + "}\n"
            + "type Foo {\n"
            + "  bar: String @cacheControl(maxAge: 5)\n"
            + "}";
    String query = "{topLevel { foo { bar } } }";
    assertEquals("max-age=5, private", execute(schema, query));
  }

  @Test
  void inheritMaxAgeOnTypes() {
    String schema =
        "type Query {\n"
            + "  topLevel: TopLevel @cacheControl(maxAge: 500)\n"
            + "}\n"
            + "type TopLevel {\n"
            + "  foo: Foo\n"
            + "}\n"
            + "type Foo @cacheControl(inheritMaxAge: true) {\n"
            + "  bar: String\n"
            + "}";
    String query = "{topLevel { foo { bar } } }";
    assertEquals("max-age=500, public", execute(schema, query));
  }

  @Test
  void scalarInheritFromGrandparents() {
    String schema =
        "type Query {\n"
            + "  foo: Foo @cacheControl(maxAge: 5)\n"
            + "}\n"
            + "type Foo {\n"
            + "  bar: Bar @cacheControl(inheritMaxAge: true)\n"
            + "  defaultBar: Bar\n"
            + "}\n"
            + "type Bar {\n"
            + "  scalar: String\n"
            + "  cachedScalar: String @cacheControl(maxAge: 2)\n"
            + "}";
    assertNull(execute(schema, "{foo{defaultBar{scalar}}}"));
    assertNull(execute(schema, "{foo{defaultBar{cachedScalar}}}"));
    assertEquals("max-age=5, public", execute(schema, "{foo{bar{scalar}}}"));
    assertEquals("max-age=2, public", execute(schema, "{foo{bar{cachedScalar}}}"));
  }

  @Test
  void entities() {
    String schema =
        "type Product @key(fields: \"id\") @cacheControl(maxAge: 60) {"
            + "  id: ID!"
            + "  name: String"
            + "}"
            + ""
            + "type User @key(fields: \"id\") @cacheControl(maxAge: 30) {"
            + "  id: ID!"
            + "  name: String"
            + "}";
    String query =
        "query ($rs: [_Any!]!) { _entities(representations: $rs) { ... on Product { id } ... on User { id } } }";

    HashMap<String, Object> variables = new HashMap<>();
    ArrayList<Object> rs = new ArrayList<>();

    HashMap<Object, Object> product = new HashMap<>();
    product.put("__typename", "Product");
    product.put("id", "1");

    HashMap<Object, Object> user = new HashMap<>();
    user.put("__typename", "User");
    user.put("id", "2");

    rs.add(product);
    rs.add(user);

    variables.put("rs", rs);
    assertEquals("max-age=30, public", execute(schema, query, 0, variables));
  }
}
