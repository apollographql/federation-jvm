package com.apollographql.federation.graphqljava.tracing;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

import com.google.protobuf.Timestamp;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.schema.GraphQLTypeUtil;
import graphql.validation.ValidationError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederatedTracingInstrumentation extends SimplePerformantInstrumentation {
  public static final String FEDERATED_TRACING_HEADER_NAME = "apollo-federation-include-trace";
  public static final String FEDERATED_TRACING_HEADER_VALUE = "ftv1";

  private static final String EXTENSION_KEY = "ftv1";

  private final Options options;

  private static final Logger logger =
      LoggerFactory.getLogger(FederatedTracingInstrumentation.class);

  public FederatedTracingInstrumentation() {
    this.options = Options.newOptions();
  }

  public FederatedTracingInstrumentation(Options options) {
    this.options = options;
  }

  @Override
  public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
    if (options.shouldTrace(parameters.getExecutionInput())) {
      return new FederatedTracingState();
    }
    // Note that we interpret null state elsewhere to mean "do not instrument".
    return null;
  }

  @Override
  public @NotNull CompletableFuture<ExecutionResult> instrumentExecutionResult(
      ExecutionResult executionResult,
      InstrumentationExecutionParameters parameters,
      InstrumentationState state) {
    final @Nullable FederatedTracingState federatedTracingState = (FederatedTracingState) state;
    if (federatedTracingState == null) {
      return super.instrumentExecutionResult(executionResult, parameters, null);
    }

    Reports.Trace trace = federatedTracingState.toProto();

    if (options.isDebuggingEnabled()) {
      logger.debug(trace.toString());
    }

    return CompletableFuture.completedFuture(
        ExecutionResultImpl.newExecutionResult()
            .from(executionResult)
            .addExtension(EXTENSION_KEY, Base64.getEncoder().encodeToString(trace.toByteArray()))
            .build());
  }

  @Override
  public @Nullable FieldFetchingInstrumentationContext beginFieldFetching(
      InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
    final @Nullable FederatedTracingState federatedTracingState = (FederatedTracingState) state;
    if (federatedTracingState != null
        && options.getTrivialFieldFetchMode()
            == TrivialFieldFetchMode.OMIT_SUCCESSFUL_TRIVIAL_LEAVES
        && parameters.isTrivialDataFetcher()) {
      return new TrivialFieldFetchContext(federatedTracingState, parameters);
    }
    // Everything else goes through beginFieldFetch, so subclasses that override it keep working.
    return super.beginFieldFetching(parameters, state);
  }

  @Override
  public InstrumentationContext<Object> beginFieldFetch(
      InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
    final @Nullable FederatedTracingState federatedTracingState = (FederatedTracingState) state;
    if (federatedTracingState == null) {
      return super.beginFieldFetch(parameters, null);
    }

    SourceLocation fieldLocation = parameters.getEnvironment().getField().getSourceLocation();

    long startNanos = System.nanoTime();
    return whenCompleted(
        (result, throwable) -> {
          long endNanos = System.nanoTime();
          addFieldFetchData(
              federatedTracingState,
              parameters,
              startNanos,
              endNanos,
              result,
              throwable,
              fieldLocation);
        });
  }

  private void addFieldFetchData(
      FederatedTracingState federatedTracingState,
      InstrumentationFieldFetchParameters parameters,
      long startNanos,
      long endNanos,
      Object result,
      Throwable throwable,
      SourceLocation fieldLocation) {
    ExecutionStepInfo executionStepInfo = parameters.getEnvironment().getExecutionStepInfo();
    federatedTracingState.addFieldFetchData(
        executionStepInfo,
        // relative to the trace's start_time, in ns
        startNanos - federatedTracingState.getStartRequestNanos(),
        // relative to the trace's start_time, in ns
        endNanos - federatedTracingState.getStartRequestNanos(),
        convertErrors(throwable, result),
        fieldLocation);
  }

  /**
   * Traces a trivial field fetch unless it is a successful, synchronous fetch of a scalar or enum.
   */
  private final class TrivialFieldFetchContext implements FieldFetchingInstrumentationContext {
    private final FederatedTracingState federatedTracingState;
    private final InstrumentationFieldFetchParameters parameters;
    private final long startNanos;
    // Async values may complete on another thread, so this is volatile.
    private volatile boolean fetchedAsync;

    private TrivialFieldFetchContext(
        FederatedTracingState federatedTracingState,
        InstrumentationFieldFetchParameters parameters) {
      this.federatedTracingState = federatedTracingState;
      this.parameters = parameters;
      this.startNanos = System.nanoTime();
    }

    @Override
    public void onDispatched() {}

    @Override
    public void onFetchedValue(Object fetchedValue) {
      // A trivial getter that returns a future or publisher may stand for real async work, so its
      // node is always kept to preserve that latency.
      fetchedAsync =
          fetchedValue instanceof CompletableFuture
              || fetchedValue instanceof Flow.Publisher
              || fetchedValue instanceof Publisher;
    }

    @Override
    public void onCompleted(Object result, Throwable throwable) {
      // Taken before the leaf check, which builds the DataFetchingEnvironment, so that building it
      // isn't counted in the duration of kept nodes.
      long endNanos = System.nanoTime();
      // graphql-java only exposes the field type through the DataFetchingEnvironment, so the leaf
      // check still builds it for successful fetches. Checking success first skips that work for
      // fetches that are kept anyway.
      if (!fetchedAsync && isSuccessfulFetch(parameters, result, throwable) && isLeafField()) {
        return;
      }
      addFieldFetchData(
          federatedTracingState,
          parameters,
          startNanos,
          endNanos,
          result,
          throwable,
          parameters.getEnvironment().getField().getSourceLocation());
    }

    /**
     * Returns whether the field is a scalar or enum (or a list of them). Only these fields can be
     * omitted, as the trace tree requires a node for every field that is an ancestor of a traced
     * field.
     */
    private boolean isLeafField() {
      return GraphQLTypeUtil.isLeaf(parameters.getEnvironment().getFieldType());
    }
  }

  /** Returns whether a fetch produced a non-null value without reporting any errors. */
  private static boolean isSuccessfulFetch(
      InstrumentationFieldFetchParameters parameters, Object result, Throwable throwable) {
    if (hasFetchErrors(throwable, result)) {
      return false;
    }

    Object data =
        result instanceof DataFetcherResult<?> ? ((DataFetcherResult<?>) result).getData() : result;

    // Unbox so that an empty Optional, which ends up as null in the response, counts as null here.
    // This goes through the configured ValueUnboxer, as a custom one may unbox other types.
    return data != null && parameters.getExecutionContext().getValueUnboxer().unbox(data) != null;
  }

  /** Returns whether a fetch threw or returned a {@link DataFetcherResult} with errors. */
  private static boolean hasFetchErrors(Throwable throwable, Object result) {
    return throwable != null
        || (result instanceof DataFetcherResult<?> && ((DataFetcherResult<?>) result).hasErrors());
  }

  @Override
  public InstrumentationContext<Document> beginParse(
      InstrumentationExecutionParameters parameters, InstrumentationState state) {
    final @Nullable FederatedTracingState federatedTracingState = (FederatedTracingState) state;
    if (federatedTracingState == null) {
      return super.beginParse(parameters, null);
    }

    return whenCompleted(
        (document, throwable) -> {
          for (GraphQLError error : convertErrors(throwable, null)) {
            federatedTracingState.addRootError(error);
          }
        });
  }

  @Override
  public InstrumentationContext<List<ValidationError>> beginValidation(
      InstrumentationValidationParameters parameters, InstrumentationState state) {
    final @Nullable FederatedTracingState federatedTracingState = (FederatedTracingState) state;
    if (federatedTracingState == null) {
      return super.beginValidation(parameters, null);
    }

    return whenCompleted(
        (validationErrors, throwable) -> {
          for (GraphQLError error : convertErrors(throwable, null)) {
            federatedTracingState.addRootError(error);
          }

          for (ValidationError error : validationErrors) {
            federatedTracingState.addRootError(error);
          }
        });
  }

  // Field resolvers can throw exceptions or add errors to the DataFetchingResult. This method
  // normalizes them to a single list of GraphQLErrors.
  @NotNull
  private List<GraphQLError> convertErrors(Throwable throwable, Object result) {
    if (!hasFetchErrors(throwable, result)) {
      return Collections.emptyList();
    }

    ArrayList<GraphQLError> graphQLErrors = new ArrayList<>();

    if (throwable != null) {
      if (throwable instanceof GraphQLError) {
        graphQLErrors.add((GraphQLError) throwable);
      } else {
        String message = throwable.getMessage();
        if (message == null) {
          message = "(null)";
        }
        GraphqlErrorBuilder errorBuilder = GraphqlErrorBuilder.newError().message(message);

        if (throwable instanceof InvalidSyntaxException) {
          errorBuilder.location(((InvalidSyntaxException) throwable).getLocation());
        }

        graphQLErrors.add(errorBuilder.build());
      }
    }

    if (result instanceof DataFetcherResult<?>) {
      DataFetcherResult<?> theResult = (DataFetcherResult<?>) result;
      if (theResult.hasErrors()) {
        graphQLErrors.addAll(theResult.getErrors());
      }
    }

    return graphQLErrors;
  }

  /** Stores timing information and a map of nodes for field information. */
  private static class FederatedTracingState implements InstrumentationState {
    private final Instant startRequestTime;
    private final long startRequestNanos;
    private final ProtoBuilderTree protoBuilderTree;

    private FederatedTracingState() {
      // record start time when creating instrumentation state for a request
      startRequestTime = Instant.now();
      startRequestNanos = System.nanoTime();

      protoBuilderTree = new ProtoBuilderTree();
    }

    @NotNull
    Reports.Trace toProto() {
      return Reports.Trace.newBuilder()
          .setStartTime(getStartTimestamp())
          .setEndTime(getNowTimestamp())
          .setDurationNs(getDuration())
          .setRoot(protoBuilderTree.toProto())
          .build();
    }

    /** Adds stats data collected from a field fetch. */
    void addFieldFetchData(
        ExecutionStepInfo stepInfo,
        long startFieldNanos,
        long endFieldNanos,
        List<GraphQLError> errors,
        SourceLocation fieldLocation) {
      ResultPath path = stepInfo.getPath();
      protoBuilderTree.editBuilder(
          path,
          (builder) -> {
            builder
                .setStartTime(startFieldNanos)
                .setEndTime(endFieldNanos)
                .setParentType(simplePrint(stepInfo.getParent().getUnwrappedNonNullType()))
                .setType(stepInfo.simplePrint())
                .setResponseName(stepInfo.getResultKey());

            // set originalFieldName only when a field alias was used
            String originalFieldName = stepInfo.getField().getName();
            if (!originalFieldName.equals(stepInfo.getResultKey())) {
              builder.setOriginalFieldName(originalFieldName);
            }

            errors.forEach(
                error -> {
                  Reports.Trace.Error.Builder errorBuilder =
                      builder.addErrorBuilder().setMessage(error.getMessage());
                  List<SourceLocation> locations = error.getLocations();
                  if ((locations == null || locations.isEmpty()) && fieldLocation != null) {
                    errorBuilder
                        .addLocationBuilder()
                        .setColumn(fieldLocation.getColumn())
                        .setLine(fieldLocation.getLine());
                  } else if (locations != null) {
                    error
                        .getLocations()
                        .forEach(
                            location ->
                                errorBuilder
                                    .addLocationBuilder()
                                    .setColumn(location.getColumn())
                                    .setLine(location.getLine()));
                  }
                });
          });
    }

    void addRootError(GraphQLError error) {
      protoBuilderTree.editBuilder(
          ResultPath.rootPath(),
          (builder) -> {
            Reports.Trace.Error.Builder errorBuilder =
                builder.addErrorBuilder().setMessage(error.getMessage());

            if (error.getLocations() != null) {
              error
                  .getLocations()
                  .forEach(
                      location ->
                          errorBuilder
                              .addLocationBuilder()
                              .setColumn(location.getColumn())
                              .setLine(location.getLine()));
            }
          });
    }

    long getStartRequestNanos() {
      return startRequestNanos;
    }

    @NotNull
    private static Timestamp instantToTimestamp(@NotNull Instant startRequestTime2) {
      return Timestamp.newBuilder()
          .setSeconds(startRequestTime2.getEpochSecond())
          .setNanos(startRequestTime2.getNano())
          .build();
    }

    @NotNull
    private Timestamp getStartTimestamp() {
      return instantToTimestamp(startRequestTime);
    }

    @NotNull
    private Timestamp getNowTimestamp() {
      return instantToTimestamp(Instant.now());
    }

    private long getDuration() {
      return System.nanoTime() - startRequestNanos;
    }

    /** A thread-safe class for storing field information and converting it to a protobuf. */
    private static class ProtoBuilderTree {
      private final Node root;
      private final ConcurrentMap<ResultPath, Node> nodesByPath;
      // We use a whole-tree read-write lock to prevent the protobuf conversion step from
      // having to acquire each node's lock.
      private final ReadWriteLock treeLock;
      // This flag is used to ensure protobuf conversion occurs once, and that builders can't
      // be edited afterwards. We intentionally leave this field to be default initialized as
      // false.
      private boolean isFinalized;

      public ProtoBuilderTree() {
        root = new Node(Reports.Trace.Node.newBuilder());
        nodesByPath = new ConcurrentHashMap<>();
        nodesByPath.put(ResultPath.rootPath(), root);
        treeLock = new ReentrantReadWriteLock();
      }

      /** Edit builder for the node at the given path (creating it and its parents if needed). */
      public void editBuilder(
          ResultPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
        Lock l = treeLock.readLock();
        l.lock();
        try {
          if (isFinalized) {
            throw new RuntimeException("Cannot edit builder after protobuf conversion.");
          }
          Node node = getOrCreateNode(path);
          synchronized (node.builder) {
            builderConsumer.accept(node.builder);
          }
        } finally {
          l.unlock();
        }
      }

      /**
       * Get node for the given path in nodesByPath (creating it and its parents if needed).
       *
       * <p>Note that {@link #treeLock}'s read lock must be held when calling this method.
       */
      @NotNull
      private Node getOrCreateNode(ResultPath path) {
        // Fast path for when the node already exists.
        Node current = nodesByPath.get(path);
        if (current != null) {
          return current;
        }

        // Find the latest ancestor that exists in the map.
        List<Object> pathSegments = path.toList();
        int currentSegmentIndex = pathSegments.size();
        while (current == null) {
          if (currentSegmentIndex <= 0) {
            // The root path's node is inserted at construction time, so this shouldn't
            // happen.
            throw new RuntimeException("root path missing from nodesByPath?");
          }
          currentSegmentIndex--;
          ResultPath currentPath =
              ResultPath.fromList(pathSegments.subList(0, currentSegmentIndex));
          current = nodesByPath.get(currentPath);
        }

        // Travel back down to the requested node, creating child nodes along the way as
        // needed.
        for (; currentSegmentIndex < pathSegments.size(); currentSegmentIndex++) {
          Node parent = current;
          ResultPath childPath =
              ResultPath.fromList(pathSegments.subList(0, currentSegmentIndex + 1));
          Object childSegment = pathSegments.get(currentSegmentIndex);

          Reports.Trace.Node.Builder childBuilder = Reports.Trace.Node.newBuilder();
          if (childSegment instanceof Integer) {
            childBuilder.setIndex((Integer) childSegment);
          } else if (currentSegmentIndex < pathSegments.size() - 1) {
            // We've encountered a field name node that is an ancestor of the requested
            // node. However, the fetcher for that field name should have ultimately
            // called getOrCreateNode() on its node before getOrCreateNode() was called
            // on the requested node, meaning that this field name node should already
            // be in nodesByPath. Accordingly, we should never encounter such field
            // name ancestor nodes, and we throw when we do.
            throw new RuntimeException("Unexpected missing non-index " + childSegment);
          }
          Node childNode = new Node(childBuilder);

          // Note that putIfAbsent() here will give the child node if it already existed,
          // or null if it didn't (in which case the node passed to putIfAbsent() becomes
          // the child node).
          current = nodesByPath.putIfAbsent(childPath, childNode);
          if (current == null) {
            current = childNode;
            parent.children.add(childNode);
          }
        }

        return current;
      }

      /**
       * Convert the entire builder tree to protobuf, forming the parent-child relationships as
       * needed.
       *
       * <p>Note that no builder may be edited after this method is called.
       */
      @NotNull
      public Reports.Trace.Node toProto() {
        Lock l = treeLock.writeLock();
        l.lock();
        try {
          if (!isFinalized) {
            buildDescendants(root);
            isFinalized = true;
          }
          return root.builder.build();
        } finally {
          l.unlock();
        }
      }

      /**
       * Recursively build the protobuf builder for the given node, forming the parent-child
       * relationships between builders of descendant nodes as needed.
       *
       * <p>Note that {@link #treeLock}'s write lock must be held when calling this method.
       */
      private void buildDescendants(Node node) {
        for (Node childNode : node.children) {
          buildDescendants(childNode);
          node.builder.addChild(childNode.builder.build());
        }
      }

      private static class Node {
        public final Reports.Trace.Node.Builder builder;
        public final ConcurrentLinkedQueue<Node> children;

        public Node(Reports.Trace.Node.Builder builder) {
          this.builder = builder;
          this.children = new ConcurrentLinkedQueue<>();
        }
      }
    }
  }

  /** Controls which trivial field fetches, such as POJO getters, are added to the trace. */
  public enum TrivialFieldFetchMode {
    /** Adds a trace node for every field fetch. */
    TRACE_ALL,

    /**
     * Omits the trace nodes of trivial scalar and enum fetches that succeed synchronously.
     *
     * <p>A fetch is omitted only if all of these are true:
     *
     * <ul>
     *   <li>Its data fetcher is a {@link graphql.TrivialDataFetcher}.
     *   <li>The field's unwrapped type is a scalar or enum.
     *   <li>The fetched value itself isn't a {@link CompletableFuture} or a publisher. A list of
     *       futures counts as a plain list.
     *   <li>The fetch didn't throw or return a {@link DataFetcherResult} with errors.
     *   <li>The fetched value isn't null.
     * </ul>
     *
     * <p>Errors raised after the fetch, while completing its value, such as scalar or enum coercion
     * errors, don't count as fetch errors. They aren't recorded on trace nodes in either mode, but
     * in this mode the field's node is also omitted.
     *
     * <p>This reduces the cost and size of traces on subgraphs that return large lists of entities.
     * In exchange, field-level metrics such as execution counts and latency won't be reported for
     * the omitted fetches. A list whose elements select only omitted fields has no index nodes
     * either, so the trace doesn't show how many elements it had.
     *
     * <p>Trivial fetches are handled in {@link #beginFieldFetching} in this mode, so subclasses
     * that override {@link #beginFieldFetch} are only called for non-trivial fetches.
     */
    OMIT_SUCCESSFUL_TRIVIAL_LEAVES
  }

  public static class Options {
    private final boolean debuggingEnabled;
    private final @Nullable Predicate<ExecutionInput> shouldTracePredicate;
    private final TrivialFieldFetchMode trivialFieldFetchMode;

    /**
     * Configuration options for federated tracing.
     *
     * @param debuggingEnabled Enables debug logging of the generated trace (default: false).
     * @param shouldTracePredicate Predicate that controls whether to generate a trace for a given
     *     request (default: null). Note that when Apollo Gateway provides an HTTP header with name
     *     "apollo-federation-include-trace" and value "ftv1", you must enable tracing for the
     *     request, and it is your responsibility to augment your {@link ExecutionInput} or its
     *     context to contain the information necessary for this predicate to have the above
     *     behavior. The default/null behavior is to disable trace generation unless the
     *     GraphQLContext map contains "apollo-federation-include-trace" entry with a value equal to
     *     "ftv1"
     * @param trivialFieldFetchMode Controls which trivial field fetches are added to the trace
     *     (default: {@link TrivialFieldFetchMode#TRACE_ALL}).
     */
    public Options(
        boolean debuggingEnabled,
        @Nullable Predicate<ExecutionInput> shouldTracePredicate,
        @NotNull TrivialFieldFetchMode trivialFieldFetchMode) {
      this.debuggingEnabled = debuggingEnabled;
      this.shouldTracePredicate = shouldTracePredicate;
      this.trivialFieldFetchMode = Objects.requireNonNull(trivialFieldFetchMode);
    }

    /**
     * Configuration options for federated tracing that trace every field fetch.
     *
     * @param debuggingEnabled Enables debug logging of the generated trace (default: false).
     * @param shouldTracePredicate Predicate that controls whether to generate a trace for a given
     *     request (default: null). See {@link #Options(boolean, Predicate, TrivialFieldFetchMode)}.
     */
    public Options(
        boolean debuggingEnabled, @Nullable Predicate<ExecutionInput> shouldTracePredicate) {
      this(debuggingEnabled, shouldTracePredicate, TrivialFieldFetchMode.TRACE_ALL);
    }

    public Options(boolean debuggingEnabled) {
      this(debuggingEnabled, null);
    }

    public static @NotNull Options newOptions() {
      return new Options(false);
    }

    public boolean isDebuggingEnabled() {
      return debuggingEnabled;
    }

    public @NotNull TrivialFieldFetchMode getTrivialFieldFetchMode() {
      return trivialFieldFetchMode;
    }

    public boolean shouldTrace(ExecutionInput executionInput) {
      if (shouldTracePredicate != null) {
        return shouldTracePredicate.test(executionInput);
      }

      if (executionInput != null
          && executionInput.getGraphQLContext().hasKey(FEDERATED_TRACING_HEADER_NAME)) {
        return FEDERATED_TRACING_HEADER_VALUE.equals(
            executionInput.getGraphQLContext().get(FEDERATED_TRACING_HEADER_NAME));
      }
      return false;
    }
  }
}
