package com.apollographql.federation.graphqljava.tracing;

import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_NAME;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_VALUE;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.Options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apollographql.federation.graphqljava.FileUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import mdg.engine.proto.Reports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederatedTracingInstrumentationTest {
  private final String tracingSDL = FileUtils.readResource("schemas/tracingSchema.graphql");
  private GraphQL graphql;

  @BeforeEach
  void setupSchema() {
    TypeDefinitionRegistry typeDefs = new SchemaParser().parse(tracingSDL);
    RuntimeWiring resolvers =
        RuntimeWiring.newRuntimeWiring()
            .type(
                "Query",
                builder ->
                    // return two items
                    builder
                        .dataFetcher(
                            "widgets",
                            env -> {
                              ArrayList<Object> objects = new ArrayList<>(2);
                              objects.add(new Object());
                              objects.add(new Object());
                              return objects;
                            })
                        .dataFetcher(
                            "listOfLists",
                            env -> {
                              ArrayList<ArrayList<Object>> lists = new ArrayList<>(2);
                              lists.add(new ArrayList<>(2));
                              lists.add(new ArrayList<>(2));
                              lists.get(0).add(new Object());
                              lists.get(0).add(new Object());
                              lists.get(1).add(new Object());
                              lists.get(1).add(new Object());
                              return lists;
                            })
                        .dataFetcher("listOfScalars", env -> new String[] {"one", "two", "three"}))
            .type(
                "Widget",
                builder ->
                    // Widget.foo works normally, Widget.bar always throws an error
                    builder
                        .dataFetcher("foo", env -> "hello world")
                        .dataFetcher(
                            "bar",
                            env -> {
                              throw new GraphQLException("whoops");
                            }))
            .build();

    GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefs, resolvers);
    graphql =
        GraphQL.newGraphQL(graphQLSchema)
            .instrumentation(new FederatedTracingInstrumentation())
            .build();
  }

  @Test
  void testTracing() throws InvalidProtocolBufferException {
    Map<String, Object> result =
        graphql
            .execute("{ widgets { foo, baz: bar }, listOfLists { foo }, listOfScalars }")
            .toSpecification();

    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);

    String ftv1 = ((Map) extensions).get("ftv1").toString();
    byte[] decoded = Base64.getDecoder().decode(ftv1);

    Reports.Trace trace = Reports.Trace.parseFrom(decoded);
    assertTrue(trace.getStartTime().getSeconds() > 0, "Start time has seconds");
    assertTrue(trace.getStartTime().getNanos() > 0, "Start time has nanoseconds");
    assertTrue(trace.getEndTime().getSeconds() > 0, "End time has seconds");
    assertTrue(trace.getEndTime().getNanos() > 0, "End time has nanoseconds");
    assertTrue(trace.getDurationNs() > 0, "DurationNs is greater than zero");
    assertEquals(3, trace.getRoot().getChildCount());

    // widgets

    Reports.Trace.Node widgets = trace.getRoot().getChild(0);
    assertTrue(widgets.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(widgets.getEndTime() > 0, "Field end time is greater than zero");
    assertEquals("Query", widgets.getParentType());
    assertEquals("[Widget!]", widgets.getType());
    assertEquals("widgets", widgets.getResponseName());
    assertEquals(2, widgets.getChildCount());

    Reports.Trace.Node secondItem = widgets.getChild(1);
    assertEquals(1, secondItem.getIndex());
    assertEquals(2, secondItem.getChildCount());

    Reports.Trace.Node foo = secondItem.getChild(0);
    assertTrue(foo.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(foo.getEndTime() > 0, "Field end time is greater than zero");
    assertEquals("Widget", foo.getParentType());
    assertEquals("String", foo.getType());
    assertEquals("foo", foo.getResponseName());
    assertEquals(0, foo.getErrorCount());

    Reports.Trace.Node bar = secondItem.getChild(1);
    assertTrue(bar.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(bar.getEndTime() > 0, "Field end time is greater than zero");
    assertEquals("Widget", bar.getParentType());
    assertEquals("String", bar.getType());
    assertEquals("baz", bar.getResponseName());
    // Widget.bar is aliased as baz
    assertEquals("bar", bar.getOriginalFieldName());
    assertEquals(1, bar.getErrorCount());

    Reports.Trace.Error error = bar.getError(0);
    assertEquals("whoops", error.getMessage());
    assertEquals(1, error.getLocationCount());
    assertEquals(18, error.getLocation(0).getColumn());
    assertEquals(1, error.getLocation(0).getLine());

    // listOfLists

    Reports.Trace.Node listOfLists = trace.getRoot().getChild(1);
    assertEquals(0, listOfLists.getChild(0).getIndex());
    assertEquals(2, listOfLists.getChild(0).getChildCount());
    assertEquals(1, listOfLists.getChild(1).getIndex());
    assertEquals(2, listOfLists.getChild(1).getChildCount());

    assertEquals(0, listOfLists.getChild(0).getChild(0).getIndex());
    assertEquals(1, listOfLists.getChild(0).getChild(0).getChildCount());
    assertEquals(1, listOfLists.getChild(0).getChild(1).getIndex());
    assertEquals(1, listOfLists.getChild(0).getChild(1).getChildCount());

    Reports.Trace.Node deeplyNestedFoo = listOfLists.getChild(0).getChild(0).getChild(0);
    assertTrue(deeplyNestedFoo.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(deeplyNestedFoo.getEndTime() > 0, "Field end time is greater than zero");
    assertEquals("Widget", deeplyNestedFoo.getParentType());
    assertEquals("String", deeplyNestedFoo.getType());
    assertEquals("foo", deeplyNestedFoo.getResponseName());
    assertEquals(0, deeplyNestedFoo.getErrorCount());

    // listOfScalars

    Reports.Trace.Node listOfScalars = trace.getRoot().getChild(2);
    assertTrue(listOfScalars.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(listOfScalars.getEndTime() > 0, "Field end time is greater than zero");
    assertEquals("Query", listOfScalars.getParentType());
    assertEquals("[String!]!", listOfScalars.getType());
    assertEquals("listOfScalars", listOfScalars.getResponseName());
  }

  @Test
  void testTracingParseErrors() throws InvalidProtocolBufferException {
    Map<String, Object> result = graphql.execute("{ widgets { foo }").toSpecification();

    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);

    String ftv1 = ((Map) extensions).get("ftv1").toString();
    byte[] decoded = Base64.getDecoder().decode(ftv1);

    Reports.Trace trace = Reports.Trace.parseFrom(decoded);
    assertEquals(1, trace.getRoot().getErrorCount());
    Reports.Trace.Error error = trace.getRoot().getError(0);
    assertEquals(
        "Invalid Syntax : offending token '<EOF>' at line 1 column 18", error.getMessage());
    assertEquals(1, error.getLocation(0).getLine());
    assertEquals(18, error.getLocation(0).getColumn());
  }

  @Test
  void testTracingValidationErrors() throws InvalidProtocolBufferException {
    Map<String, Object> result = graphql.execute("{ widgets { notARealThing } }").toSpecification();

    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);

    String ftv1 = ((Map) extensions).get("ftv1").toString();
    byte[] decoded = Base64.getDecoder().decode(ftv1);

    Reports.Trace trace = Reports.Trace.parseFrom(decoded);
    assertEquals(1, trace.getRoot().getErrorCount());
    Reports.Trace.Error error = trace.getRoot().getError(0);
    assertEquals(
        "Validation error (FieldUndefined@[widgets/notARealThing]) : Field 'notARealThing' in type 'Widget' is undefined",
        error.getMessage());
    assertEquals(1, error.getLocation(0).getLine());
    assertEquals(13, error.getLocation(0).getColumn());
  }

  @Test
  void testOptions() {
    // This test primarily exists so that IntelliJ doesn't encourage you to take 'public' off
    // of these two methods.
    assertFalse(FederatedTracingInstrumentation.Options.newOptions().isDebuggingEnabled());
  }

  @Test
  void testHTTPHeaders() {
    Map<String, String> headers = new HashMap<>();
    HTTPRequestHeaders context = headers::get;
    ExecutionInput input =
        ExecutionInput.newExecutionInput("{widgets {foo}}").context(context).build();

    // Because the context implements HTTPRequestHeaders but the special header isn't there,
    // we don't get the trace extension (or any extensions).
    Map<String, Object> result = graphql.execute(input).toSpecification();
    assertNull(result.get("extensions"));

    // Try again with the header having the wrong value.
    headers.put("apollo-federation-include-trace", "bla");
    result = graphql.execute(input).toSpecification();
    assertNull(result.get("extensions"));

    // Now with the right value.
    headers.put("apollo-federation-include-trace", "ftv1");
    result = graphql.execute(input).toSpecification();
    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);
    assertTrue(((Map) extensions).containsKey("ftv1"));
  }

  @Test
  void testBringYourOwnContextSignalsToTracePredicate() {
    // Create instrumentation which has explicit custom evaluation predicate defined.
    Options options =
        new Options(
            false,
            (ExecutionInput executionInput) -> {
              if (executionInput.getContext() instanceof Map) {
                Map ctxMap = (Map) executionInput.getContext();
                return FEDERATED_TRACING_HEADER_VALUE.equals(
                    ctxMap.get(FEDERATED_TRACING_HEADER_NAME));
              }
              return true;
            });

    // Change the instrumentation to the above constructed one.
    graphql =
        graphql.transform(
            (GraphQL.Builder builder) ->
                builder.instrumentation(new FederatedTracingInstrumentation(options)));

    Map<String, String> context = new HashMap<>();
    ExecutionInput input =
        ExecutionInput.newExecutionInput("{widgets {foo}}").context(context).build();

    // Our context object is just a Map, and should not implement HTTPRequestHeaders.
    assertFalse(input.getContext() instanceof HTTPRequestHeaders);

    // Because the special header isn't there, we don't get the trace extension.
    Map<String, Object> result = graphql.execute(input).toSpecification();
    assertNull(result.get("extensions"));

    // Try again with the header having the wrong value.
    context.put("apollo-federation-include-trace", "bla");
    result = graphql.execute(input).toSpecification();
    assertNull(result.get("extensions"));

    // Now with the right value.
    context.put("apollo-federation-include-trace", "ftv1");
    result = graphql.execute(input).toSpecification();
    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);
    assertTrue(((Map) extensions).containsKey("ftv1"));
  }

  @Test
  void testTracingWithGraphQLContextMap() {
    ExecutionInput input = ExecutionInput.newExecutionInput("{widgets {foo}}").build();

    // Because the special header isn't there, we fallback to the default behavior
    Map<String, Object> result = graphql.execute(input).toSpecification();
    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);
    assertTrue(((Map) extensions).containsKey("ftv1"));

    // Try again with the header having the wrong value.
    Map<String, Object> context = new HashMap<>();
    context.put("apollo-federation-include-trace", "bla");

    input = input.transform((ExecutionInput.Builder builder) -> builder.graphQLContext(context));
    result = graphql.execute(input).toSpecification();
    assertNull(result.get("extensions"));

    // Now with the right value.
    context.put("apollo-federation-include-trace", "ftv1");
    input = input.transform((ExecutionInput.Builder builder) -> builder.graphQLContext(context));
    result = graphql.execute(input).toSpecification();
    extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);
    assertTrue(((Map) extensions).containsKey("ftv1"));
  }
}
