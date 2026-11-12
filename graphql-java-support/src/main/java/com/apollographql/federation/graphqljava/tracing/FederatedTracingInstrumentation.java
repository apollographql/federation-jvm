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
import graphql.validation.ValidationError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

          ExecutionStepInfo executionStepInfo = parameters.getEnvironment().getExecutionStepInfo();
          federatedTracingState.addFieldFetchData(
              executionStepInfo,
              // relative to the trace's start_time, in ns
              startNanos - federatedTracingState.getStartRequestNanos(),
              // relative to the trace's start_time, in ns
              endNanos - federatedTracingState.getStartRequestNanos(),
              convertErrors(throwable, result),
              fieldLocation);
        });
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
      // Set once, by toProto(). Read it only through checkNotFinalized(), while holding the
      // monitor of the node being edited or linked to. toProto() takes that same monitor before
      // it reads a node, so an edit either lands before its node is built or is rejected.
      private volatile boolean finalized;
      // The converted tree. Assigned only once conversion has succeeded.
      private Reports.Trace.Node proto;

      public ProtoBuilderTree() {
        root = new Node(Reports.Trace.Node.newBuilder());
      }

      /**
       * Edit builder for the node at the given path (creating it and its parents if needed).
       *
       * <p>A new node is edited before it is linked into the trie, together with any index nodes
       * created for it, so an edit that is rejected because of toProto() leaves the trie unchanged.
       */
      public void editBuilder(
          ResultPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
        Node parent = path.isRootPath() ? null : find(path.getParent());
        Node node =
            path.isRootPath() ? root : parent == null ? null : parent.child(path.getSegmentValue());
        if (node == null) {
          Reports.Trace.Node.Builder builder = Reports.Trace.Node.newBuilder();
          Object segment = path.getSegmentValue();
          if (segment instanceof Integer) {
            builder.setIndex((Integer) segment);
          }
          Node created = new Node(builder);
          builderConsumer.accept(created.builder);
          node = link(parent, path, created);
          if (node == null) {
            return;
          }
          // Another edit linked a node at this path first, so edit that one instead.
        }
        synchronized (node) {
          checkNotFinalized();
          builderConsumer.accept(node.builder);
        }
      }

      private void checkNotFinalized() {
        if (finalized) {
          throw new RuntimeException("Cannot edit builder after protobuf conversion.");
        }
      }

      /**
       * Get the node at the given path, or null if it is not in the trie. Lookups take no locks and
       * never materialize intermediate {@link ResultPath} instances.
       */
      @Nullable
      private Node find(ResultPath path) {
        if (path.isRootPath()) {
          return root;
        }
        Node parent = find(path.getParent());
        return parent == null ? null : parent.child(path.getSegmentValue());
      }

      /**
       * Link an unpublished node at the given path, first wrapping it in index nodes for any
       * missing list-index ancestors so that the whole chain becomes visible in one step.
       *
       * @param parent the node at the path's parent, or null if there is none yet
       * @return null if the node was linked, otherwise the node already at that path
       */
      @Nullable
      private Node link(@Nullable Node parent, ResultPath path, Node node) {
        Object segment = path.getSegmentValue();
        if (parent == null) {
          ResultPath parentPath = path.getParent();
          Object parentSegment = parentPath.getSegmentValue();
          if (!(parentSegment instanceof Integer)) {
            // We've encountered a field name node that is an ancestor of the requested
            // node. However, the fetcher for that field name should have ultimately
            // called editBuilder() on its node before editBuilder() was called on the
            // requested node, meaning that this field name node should already be in
            // the trie. Accordingly, we should never encounter such field name ancestor
            // nodes, and we throw when we do.
            throw new RuntimeException("Unexpected missing non-index " + parentSegment);
          }
          Node indexNode =
              new Node(Reports.Trace.Node.newBuilder().setIndex((Integer) parentSegment));
          indexNode.linkIfAbsent(segment, node);
          parent = link(find(parentPath.getParent()), parentPath, indexNode);
          if (parent == null) {
            return null;
          }
          // Another edit linked this index node first, so link under that one instead.
        }
        synchronized (parent) {
          checkNotFinalized();
          return parent.linkIfAbsent(segment, node);
        }
      }

      /**
       * Convert the entire builder tree to protobuf, forming the parent-child relationships as
       * needed.
       *
       * <p>Note that no builder may be edited after this method is called; a late edit throws. This
       * method is synchronized so that the conversion happens once; it is off the per-field hot
       * path.
       */
      @NotNull
      public synchronized Reports.Trace.Node toProto() {
        if (proto == null) {
          finalized = true;
          proto = build(root);
        }
        return proto;
      }

      /** Recursively convert the given node and its descendants. */
      private Reports.Trace.Node build(Node node) {
        synchronized (node) {
          if (node.children != null) {
            // Cleared so that a conversion retried after a failure does not add children twice.
            node.builder.clearChild();
            for (Node child : node.children) {
              node.builder.addChild(build(child));
            }
          }
          return node.builder.build();
        }
      }

      private static class Node {
        public final Reports.Trace.Node.Builder builder;
        // Children keyed by result path segment value (String for fields, Integer for list
        // indices), used for lookup. Both collections are created on the first child, so leaf
        // nodes carry neither.
        private volatile ConcurrentMap<Object, Node> childrenBySegment;
        // Children in insertion order, used for protobuf output. Guarded by this node's monitor.
        private List<Node> children;

        public Node(Reports.Trace.Node.Builder builder) {
          this.builder = builder;
        }

        @Nullable
        Node child(Object segment) {
          ConcurrentMap<Object, Node> bySegment = childrenBySegment;
          return bySegment == null ? null : bySegment.get(segment);
        }

        /**
         * Add a child unless one already exists for the segment, returning the existing child if so
         * and null otherwise. The caller must hold this node's monitor, unless the node has not
         * been linked into the trie yet.
         */
        @Nullable
        Node linkIfAbsent(Object segment, Node child) {
          ConcurrentMap<Object, Node> bySegment = childrenBySegment;
          if (bySegment == null) {
            children = new ArrayList<>();
            childrenBySegment = bySegment = new ConcurrentHashMap<>();
          }
          Node existing = bySegment.putIfAbsent(segment, child);
          if (existing == null) {
            children.add(child);
          }
          return existing;
        }
      }
    }
  }

  public static class Options {
    private final boolean debuggingEnabled;
    private final @Nullable Predicate<ExecutionInput> shouldTracePredicate;

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
     */
    public Options(
        boolean debuggingEnabled, @Nullable Predicate<ExecutionInput> shouldTracePredicate) {
      this.debuggingEnabled = debuggingEnabled;
      this.shouldTracePredicate = shouldTracePredicate;
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
