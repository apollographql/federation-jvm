package com.apollographql.federation.graphqljava.tracing;

import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_NAME;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_VALUE;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.Options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apollographql.federation.graphqljava.FileUtils;
import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FederatedTracingState.ProtoBuilderTree;
import com.google.protobuf.InvalidProtocolBufferException;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ResultPath;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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
            .execute(
                createExecutionInput(
                    "{ widgets { foo, baz: bar }, listOfLists { foo }, listOfScalars }"))
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
    Map<String, Object> result =
        graphql.execute(createExecutionInput("{ widgets { foo }")).toSpecification();

    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);

    String ftv1 = ((Map) extensions).get("ftv1").toString();
    byte[] decoded = Base64.getDecoder().decode(ftv1);

    Reports.Trace trace = Reports.Trace.parseFrom(decoded);
    assertEquals(1, trace.getRoot().getErrorCount());
    Reports.Trace.Error error = trace.getRoot().getError(0);
    assertEquals(
        "Invalid syntax with offending token '<EOF>' at line 1 column 18", error.getMessage());
    assertEquals(1, error.getLocation(0).getLine());
    assertEquals(18, error.getLocation(0).getColumn());
  }

  @Test
  void testTracingValidationErrors() throws InvalidProtocolBufferException {
    Map<String, Object> result =
        graphql.execute(createExecutionInput("{ widgets { notARealThing } }")).toSpecification();

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

    Map<String, Object> result = graphql.execute(input).toSpecification();
    Object extensions = result.get("extensions");
    // Because the special header isn't there, we fallback to the default behavior of not generating
    // ftv1
    assertNull(extensions);

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

  @Test
  void testChildOrderingForWideListsAndNestedLists() throws InvalidProtocolBufferException {
    int widgetCount = 50;
    GraphQL wide = buildGraphQL(widgetCount, 3, 4, null);

    Reports.Trace trace =
        parseTrace(
            wide.execute(createExecutionInput("{ widgets { foo, bar }, listOfLists { bar, foo } }"))
                .toSpecification());

    assertEquals(2, trace.getRoot().getChildCount());

    Reports.Trace.Node widgets = trace.getRoot().getChild(0);
    assertEquals("widgets", widgets.getResponseName());
    assertEquals(widgetCount, widgets.getChildCount());
    for (int i = 0; i < widgetCount; i++) {
      Reports.Trace.Node item = widgets.getChild(i);
      assertEquals(i, item.getIndex(), "widgets[" + i + "] index");
      assertEquals(2, item.getChildCount(), "widgets[" + i + "] child count");
      assertEquals("foo", item.getChild(0).getResponseName());
      assertEquals("bar", item.getChild(1).getResponseName());
    }

    Reports.Trace.Node listOfLists = trace.getRoot().getChild(1);
    assertEquals("listOfLists", listOfLists.getResponseName());
    assertEquals(3, listOfLists.getChildCount());
    for (int i = 0; i < 3; i++) {
      Reports.Trace.Node outer = listOfLists.getChild(i);
      assertEquals(i, outer.getIndex(), "listOfLists[" + i + "] index");
      assertEquals(4, outer.getChildCount());
      for (int j = 0; j < 4; j++) {
        Reports.Trace.Node inner = outer.getChild(j);
        assertEquals(j, inner.getIndex(), "listOfLists[" + i + "][" + j + "] index");
        assertEquals(2, inner.getChildCount());
        // selection order in the query is bar, foo
        assertEquals("bar", inner.getChild(0).getResponseName());
        assertEquals("foo", inner.getChild(1).getResponseName());
      }
    }
  }

  @Test
  void testAliasedAndErrorBearingFieldNodesAreStructurallyUnchanged()
      throws InvalidProtocolBufferException {
    Reports.Trace trace =
        parseTrace(
            graphql
                .execute(createExecutionInput("{ widgets { foo, baz: bar } }"))
                .toSpecification());

    Reports.Trace.Node widgets = trace.getRoot().getChild(0);
    assertEquals(2, widgets.getChildCount());

    Reports.Trace.Node expectedItem0 =
        Reports.Trace.Node.newBuilder()
            .setIndex(0)
            .addChild(
                Reports.Trace.Node.newBuilder()
                    .setResponseName("foo")
                    .setType("String")
                    .setParentType("Widget"))
            .addChild(
                Reports.Trace.Node.newBuilder()
                    .setResponseName("baz")
                    .setOriginalFieldName("bar")
                    .setType("String")
                    .setParentType("Widget")
                    .addError(
                        Reports.Trace.Error.newBuilder()
                            .setMessage("whoops")
                            .addLocation(
                                Reports.Trace.Location.newBuilder().setLine(1).setColumn(18))))
            .build();
    Reports.Trace.Node expectedItem1 = expectedItem0.toBuilder().setIndex(1).build();

    assertEquals(expectedItem0, withoutTimings(widgets.getChild(0)));
    assertEquals(expectedItem1, withoutTimings(widgets.getChild(1)));
  }

  @Test
  void testConcurrentFieldFetchesWithAsyncExecutionStrategy()
      throws InvalidProtocolBufferException {
    int widgetCount = 500;
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      GraphQL async = buildGraphQL(widgetCount, 2, 2, executor);

      graphql.ExecutionResult executionResult =
          async.execute(createExecutionInput("{ widgets { foo, bar }, listOfLists { foo } }"));
      assertTrue(
          executionResult.getErrors().isEmpty(), () -> executionResult.getErrors().toString());

      Reports.Trace trace = parseTrace(executionResult.toSpecification());

      // Field nodes: widgets, listOfLists, 2 per widget, 1 per nested list item.
      int expectedFieldNodes = 2 + widgetCount * 2 + 2 * 2;
      // Index nodes: one per widget, one per outer list, one per inner item.
      int expectedIndexNodes = widgetCount + 2 + 2 * 2;
      int[] counts = new int[2];
      countNodes(trace.getRoot(), counts);
      assertEquals(expectedFieldNodes, counts[0], "field node count");
      assertEquals(expectedIndexNodes, counts[1], "index node count");

      Reports.Trace.Node widgets = trace.getRoot().getChild(0);
      assertEquals(widgetCount, widgets.getChildCount());
      for (int i = 0; i < widgetCount; i++) {
        assertEquals(2, widgets.getChild(i).getChildCount());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void testEditAfterProtoConversionThrows() {
    ProtoBuilderTree tree = new ProtoBuilderTree();

    ResultPath fieldPath = ResultPath.rootPath().segment("widgets");
    Consumer<Reports.Trace.Node.Builder> setName = builder -> builder.setResponseName("widgets");
    tree.editBuilder(fieldPath, setName);

    Reports.Trace.Node proto = tree.toProto();
    assertEquals(1, proto.getChildCount());
    assertEquals("widgets", proto.getChild(0).getResponseName());
    // Conversion is idempotent.
    assertEquals(proto, tree.toProto());

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> tree.editBuilder(fieldPath, setName));
    assertEquals("Cannot edit builder after protobuf conversion.", thrown.getMessage());
  }

  @Test
  void testMissingNonIndexAncestorThrows() {
    ProtoBuilderTree tree = new ProtoBuilderTree();

    ResultPath orphan = ResultPath.rootPath().segment("widgets").segment(0).segment("foo");
    Consumer<Reports.Trace.Node.Builder> noop = builder -> {};
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> tree.editBuilder(orphan, noop));
    assertEquals("Unexpected missing non-index widgets", thrown.getMessage());
  }

  /**
   * Builds a GraphQL instance over the tracing schema with {@code widgetCount} widgets and a {@code
   * outer x inner} list of lists. When {@code executor} is non-null, every data fetcher returns a
   * CompletableFuture completed on that executor and Widget.bar does not throw.
   */
  private GraphQL buildGraphQL(
      int widgetCount,
      int outer,
      int inner,
      @org.jetbrains.annotations.Nullable ExecutorService executor) {
    TypeDefinitionRegistry typeDefs = new SchemaParser().parse(tracingSDL);
    RuntimeWiring resolvers =
        RuntimeWiring.newRuntimeWiring()
            .type(
                "Query",
                builder ->
                    builder
                        .dataFetcher(
                            "widgets",
                            env -> {
                              List<Object> objects = new ArrayList<>(widgetCount);
                              for (int i = 0; i < widgetCount; i++) {
                                objects.add(new Object());
                              }
                              return maybeAsync(objects, executor);
                            })
                        .dataFetcher(
                            "listOfLists",
                            env -> {
                              List<List<Object>> lists = new ArrayList<>(outer);
                              for (int i = 0; i < outer; i++) {
                                List<Object> list = new ArrayList<>(inner);
                                for (int j = 0; j < inner; j++) {
                                  list.add(new Object());
                                }
                                lists.add(list);
                              }
                              return maybeAsync(lists, executor);
                            })
                        .dataFetcher("listOfScalars", env -> new String[] {"one"}))
            .type(
                "Widget",
                builder ->
                    builder
                        .dataFetcher("foo", env -> maybeAsync("hello world", executor))
                        .dataFetcher(
                            "bar",
                            env -> {
                              if (executor == null) {
                                throw new GraphQLException("whoops");
                              }
                              return maybeAsync("bar", executor);
                            }))
            .build();
    GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeDefs, resolvers);
    return GraphQL.newGraphQL(schema)
        .queryExecutionStrategy(new AsyncExecutionStrategy())
        .instrumentation(new FederatedTracingInstrumentation())
        .build();
  }

  private static Object maybeAsync(
      Object value, @org.jetbrains.annotations.Nullable ExecutorService executor) {
    if (executor == null) {
      return value;
    }
    return CompletableFuture.supplyAsync(() -> value, executor);
  }

  private static Reports.Trace parseTrace(Map<String, Object> result)
      throws InvalidProtocolBufferException {
    Object extensions = result.get("extensions");
    assertTrue(extensions instanceof Map);
    String ftv1 = ((Map) extensions).get("ftv1").toString();
    return Reports.Trace.parseFrom(Base64.getDecoder().decode(ftv1));
  }

  /** Counts field nodes (counts[0]) and index nodes (counts[1]) below the given node. */
  private static void countNodes(Reports.Trace.Node node, int[] counts) {
    for (Reports.Trace.Node child : node.getChildList()) {
      if (child.getIdCase() == Reports.Trace.Node.IdCase.INDEX) {
        counts[1]++;
      } else {
        counts[0]++;
      }
      countNodes(child, counts);
    }
  }

  private static Reports.Trace.Node withoutTimings(Reports.Trace.Node node) {
    Reports.Trace.Node.Builder builder = node.toBuilder().clearStartTime().clearEndTime();
    builder.clearChild();
    for (Reports.Trace.Node child : node.getChildList()) {
      builder.addChild(withoutTimings(child));
    }
    return builder.build();
  }

  private static ExecutionInput createExecutionInput(String query) {
    return ExecutionInput.newExecutionInput(query)
        .graphQLContext(Map.of(FEDERATED_TRACING_HEADER_NAME, FEDERATED_TRACING_HEADER_VALUE))
        .build();
  }
}
