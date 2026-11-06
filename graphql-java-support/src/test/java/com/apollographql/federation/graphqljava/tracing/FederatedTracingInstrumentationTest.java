package com.apollographql.federation.graphqljava.tracing;

import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_NAME;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_VALUE;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.Options;
import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.TrivialFieldFetchMode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apollographql.federation.graphqljava.FileUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetchingEnvironment;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import mdg.engine.proto.Reports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederatedTracingInstrumentationTest {
  private static final long SLOW_FETCH_MILLIS = 20;
  private static final String TRIVIAL_LEAVES_QUERY =
      "{ gadgets { id status tags wrapped asyncValue resolved } }";

  private final String tracingSDL = FileUtils.readResource("schemas/tracingSchema.graphql");
  private final String trivialFetchSDL =
      FileUtils.readResource("schemas/tracingTrivialFetchSchema.graphql");
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
  void testOptionsTraceAllTrivialFieldFetchesByDefault() {
    assertEquals(TrivialFieldFetchMode.TRACE_ALL, Options.newOptions().getTrivialFieldFetchMode());
    assertEquals(TrivialFieldFetchMode.TRACE_ALL, new Options(true).getTrivialFieldFetchMode());
    assertEquals(
        TrivialFieldFetchMode.TRACE_ALL, new Options(false, null).getTrivialFieldFetchMode());
    assertThrows(NullPointerException.class, () -> new Options(false, null, null));
  }

  @Test
  void testTrivialLeavesAreTracedByDefault() throws InvalidProtocolBufferException {
    Reports.Trace.Node defaultGadget =
        firstGadget(execute(new FederatedTracingInstrumentation(), TRIVIAL_LEAVES_QUERY));
    Reports.Trace.Node traceAllGadget =
        firstGadget(execute(TrivialFieldFetchMode.TRACE_ALL, TRIVIAL_LEAVES_QUERY));

    Set<String> expected = Set.of("id", "status", "tags", "wrapped", "asyncValue", "resolved");
    assertEquals(expected, responseNames(defaultGadget));
    assertEquals(expected, responseNames(traceAllGadget));
  }

  @Test
  void testSuccessfulTrivialLeavesAreOmitted() throws InvalidProtocolBufferException {
    Reports.Trace trace = executeOmittingTrivialLeaves(TRIVIAL_LEAVES_QUERY);

    Reports.Trace.Node gadgets = child(trace.getRoot(), "gadgets");
    assertEquals(2, gadgets.getChildCount());
    gadgets
        .getChildList()
        .forEach(gadget -> assertEquals(Set.of("resolved"), responseNames(gadget)));
  }

  @Test
  void testOmittingTrivialLeavesKeepsResponseData() {
    Map<String, Object> data =
        createTrivialFetchGraphQL(
                new FederatedTracingInstrumentation(
                    new Options(false, null, TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES)))
            .execute(createExecutionInput(TRIVIAL_LEAVES_QUERY))
            .toSpecification();

    assertNull(data.get("errors"));
    Map<String, Object> gadget =
        ((List<Map<String, Object>>) ((Map<String, Object>) data.get("data")).get("gadgets"))
            .get(0);
    assertEquals("gadget-id", gadget.get("id"));
    assertEquals("ACTIVE", gadget.get("status"));
    assertEquals(List.of("a", "b"), gadget.get("tags"));
    assertEquals("wrapped", gadget.get("wrapped"));
    assertEquals("async", gadget.get("asyncValue"));
    assertEquals("resolved", gadget.get("resolved"));
  }

  @Test
  void testTrivialObjectParentsAreKept() throws InvalidProtocolBufferException {
    Reports.Trace trace =
        executeOmittingTrivialLeaves(
            "{ gadgets { detail { label resolved } emptyDetail { label } } }");

    Reports.Trace.Node gadget = firstGadget(trace);
    assertEquals(Set.of("detail", "emptyDetail"), responseNames(gadget));

    Reports.Trace.Node detail = child(gadget, "detail");
    assertEquals("Detail", detail.getType());
    assertEquals(Set.of("resolved"), responseNames(detail));
  }

  @Test
  void testTrivialObjectWithOnlyOmittedChildrenIsKept() throws InvalidProtocolBufferException {
    Reports.Trace trace = executeOmittingTrivialLeaves("{ gadgets { detail { label } } }");

    assertEquals(0, child(firstGadget(trace), "detail").getChildCount());
  }

  @Test
  void testTrivialLeavesThatReturnNullAreKept() throws InvalidProtocolBufferException {
    Reports.Trace trace =
        executeOmittingTrivialLeaves("{ gadgets { id missing emptyOptional withNullData } }");

    Reports.Trace.Node gadget = firstGadget(trace);
    assertEquals(Set.of("missing", "emptyOptional", "withNullData"), responseNames(gadget));
    gadget.getChildList().forEach(node -> assertEquals(0, node.getErrorCount()));
  }

  @Test
  void testTrivialLeavesWithErrorsAreKept() throws InvalidProtocolBufferException {
    Reports.Trace trace =
        executeOmittingTrivialLeaves("{ gadgets { id throwing asyncThrowing withErrors } }");

    Reports.Trace.Node gadget = firstGadget(trace);
    assertEquals(Set.of("throwing", "asyncThrowing", "withErrors"), responseNames(gadget));

    Reports.Trace.Node throwing = child(gadget, "throwing");
    assertEquals("Gadget", throwing.getParentType());
    assertEquals("String", throwing.getType());
    assertEquals(1, throwing.getErrorCount());
    assertEquals("whoops", throwing.getError(0).getMessage());
    assertEquals(1, throwing.getError(0).getLocation(0).getLine());
    assertEquals(16, throwing.getError(0).getLocation(0).getColumn());

    Reports.Trace.Node asyncThrowing = child(gadget, "asyncThrowing");
    assertEquals(1, asyncThrowing.getErrorCount());
    assertTrue(asyncThrowing.getError(0).getMessage().contains("async whoops"));

    Reports.Trace.Node withErrors = child(gadget, "withErrors");
    assertEquals(1, withErrors.getErrorCount());
    assertEquals("partial whoops", withErrors.getError(0).getMessage());
  }

  @Test
  void testKeptTrivialFetchesAreTimedFromBeforeTheFetch() throws InvalidProtocolBufferException {
    Reports.Trace trace = executeOmittingTrivialLeaves("{ gadgets { slowThrowing } }");

    Reports.Trace.Node slowThrowing = child(firstGadget(trace), "slowThrowing");
    assertTrue(slowThrowing.getStartTime() > 0, "Field start time is greater than zero");
    assertTrue(
        slowThrowing.getEndTime() - slowThrowing.getStartTime()
            >= TimeUnit.MILLISECONDS.toNanos(SLOW_FETCH_MILLIS),
        "Field duration includes the fetch");
  }

  @Test
  void testDataFetchingEnvironmentIsNotReadBeforeTrivialFetch() throws Exception {
    FederatedTracingInstrumentation instrumentation =
        new FederatedTracingInstrumentation(
            new Options(false, null, TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES));
    InstrumentationState state =
        instrumentation.createState(
            new InstrumentationCreateStateParameters(null, createExecutionInput("{ __typename }")));

    AtomicInteger environmentReads = new AtomicInteger();
    Supplier<DataFetchingEnvironment> environment =
        () -> {
          environmentReads.incrementAndGet();
          throw new AssertionError("DataFetchingEnvironment should not be read");
        };

    InstrumentationContext<Object> context =
        instrumentation.beginFieldFetch(
            new InstrumentationFieldFetchParameters(null, environment, null, true), state);

    assertNotNull(context);
    assertEquals(0, environmentReads.get());
  }

  @Test
  void testNoTraceIsGeneratedWhenTracingIsNotRequested() {
    Options options =
        new Options(false, null, TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES);
    assertNull(
        createTrivialFetchGraphQL(new FederatedTracingInstrumentation(options))
            .execute(ExecutionInput.newExecutionInput(TRIVIAL_LEAVES_QUERY).build())
            .toSpecification()
            .get("extensions"));

    Options neverTrace =
        new Options(false, input -> false, TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES);
    assertNull(
        createTrivialFetchGraphQL(new FederatedTracingInstrumentation(neverTrace))
            .execute(createExecutionInput(TRIVIAL_LEAVES_QUERY))
            .toSpecification()
            .get("extensions"));
  }

  private GraphQL createTrivialFetchGraphQL(FederatedTracingInstrumentation instrumentation) {
    TypeDefinitionRegistry typeDefs = new SchemaParser().parse(trivialFetchSDL);
    RuntimeWiring resolvers =
        RuntimeWiring.newRuntimeWiring()
            .type(
                "Query",
                builder ->
                    builder.dataFetcher("gadgets", env -> List.of(new Gadget(), new Gadget())))
            .type("Gadget", builder -> builder.dataFetcher("resolved", env -> "resolved"))
            .type("Detail", builder -> builder.dataFetcher("resolved", env -> "resolved"))
            .build();

    GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefs, resolvers);
    return GraphQL.newGraphQL(graphQLSchema).instrumentation(instrumentation).build();
  }

  private Reports.Trace executeOmittingTrivialLeaves(String query)
      throws InvalidProtocolBufferException {
    return execute(TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES, query);
  }

  private Reports.Trace execute(TrivialFieldFetchMode mode, String query)
      throws InvalidProtocolBufferException {
    return execute(new FederatedTracingInstrumentation(new Options(false, null, mode)), query);
  }

  private Reports.Trace execute(FederatedTracingInstrumentation instrumentation, String query)
      throws InvalidProtocolBufferException {
    Map<Object, Object> extensions =
        createTrivialFetchGraphQL(instrumentation)
            .execute(createExecutionInput(query))
            .getExtensions();
    return Reports.Trace.parseFrom(Base64.getDecoder().decode(extensions.get("ftv1").toString()));
  }

  private static Reports.Trace.Node firstGadget(Reports.Trace trace) {
    Reports.Trace.Node gadget = child(trace.getRoot(), "gadgets").getChild(0);
    assertEquals(0, gadget.getIndex());
    return gadget;
  }

  // Async fetches add their nodes when they complete, so sibling order isn't deterministic.
  private static Reports.Trace.Node child(Reports.Trace.Node node, String responseName) {
    return node.getChildList().stream()
        .filter(child -> responseName.equals(child.getResponseName()))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> responseNames(Reports.Trace.Node node) {
    return node.getChildList().stream()
        .map(Reports.Trace.Node::getResponseName)
        .collect(Collectors.toSet());
  }

  /** Every field without an explicit data fetcher is resolved by a trivial getter. */
  public static class Gadget {
    public String getId() {
      return "gadget-id";
    }

    public String getStatus() {
      return "ACTIVE";
    }

    public List<String> getTags() {
      return List.of("a", "b");
    }

    public DataFetcherResult<String> getWrapped() {
      return DataFetcherResult.<String>newResult().data("wrapped").build();
    }

    public CompletableFuture<String> getAsyncValue() {
      return CompletableFuture.supplyAsync(() -> "async");
    }

    public Detail getDetail() {
      return new Detail();
    }

    public Detail getEmptyDetail() {
      return null;
    }

    public String getMissing() {
      return null;
    }

    public Optional<String> getEmptyOptional() {
      return Optional.empty();
    }

    public String getThrowing() {
      throw new GraphQLException("whoops");
    }

    public String getSlowThrowing() throws InterruptedException {
      TimeUnit.MILLISECONDS.sleep(SLOW_FETCH_MILLIS);
      throw new GraphQLException("slow whoops");
    }

    public CompletableFuture<String> getAsyncThrowing() {
      return CompletableFuture.supplyAsync(
          () -> {
            throw new GraphQLException("async whoops");
          });
    }

    public DataFetcherResult<String> getWithErrors() {
      return DataFetcherResult.<String>newResult()
          .data("partial")
          .error(GraphqlErrorBuilder.newError().message("partial whoops").build())
          .build();
    }

    public DataFetcherResult<String> getWithNullData() {
      return DataFetcherResult.<String>newResult().build();
    }
  }

  public static class Detail {
    public String getLabel() {
      return "label";
    }
  }

  private static ExecutionInput createExecutionInput(String query) {
    return ExecutionInput.newExecutionInput(query)
        .graphQLContext(Map.of(FEDERATED_TRACING_HEADER_NAME, FEDERATED_TRACING_HEADER_VALUE))
        .build();
  }
}
