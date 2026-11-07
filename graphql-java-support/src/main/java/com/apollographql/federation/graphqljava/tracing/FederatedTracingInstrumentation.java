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
import java.util.concurrent.ConcurrentLinkedQueue;
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
      // This flag is used to ensure protobuf conversion occurs once, and that builders can't
      // be edited afterwards. We intentionally leave this field to be default initialized as
      // false.
      //
      // No lock is needed around builder edits. Each field node's builder is written only by
      // that field's own fetch completion callback; index placeholder nodes are written once,
      // at creation, by whichever thread wins the putIfAbsent() race; root errors are added
      // from beginParse() and beginValidation(), which complete before execution starts; and
      // graphql-java invokes instrumentExecutionResult(), which calls toProto(), only after
      // all field fetches have completed. The volatile read here is the only
      // synchronization on the edit path.
      private volatile boolean finalized;

      public ProtoBuilderTree() {
        root = new Node(Reports.Trace.Node.newBuilder());
      }

      /** Edit builder for the node at the given path (creating it and its parents if needed). */
      public void editBuilder(
          ResultPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
        if (finalized) {
          throw new RuntimeException("Cannot edit builder after protobuf conversion.");
        }
        Node node = getOrCreateNode(path);
        builderConsumer.accept(node.builder);
      }

      /**
       * Get node for the given path in the trie (creating it and its parents if needed).
       *
       * <p>This walks the path's parent chain to collect segment values, then descends from the
       * root one child lookup per level. It never materializes intermediate {@link ResultPath}
       * instances.
       */
      @NotNull
      private Node getOrCreateNode(ResultPath path) {
        // Collect segment values from the requested path up to (not including) the root.
        int depth = 0;
        for (ResultPath p = path; !p.isRootPath(); p = p.getParent()) {
          depth++;
        }
        if (depth == 0) {
          return root;
        }
        Object[] segments = new Object[depth];
        int i = depth;
        for (ResultPath p = path; !p.isRootPath(); p = p.getParent()) {
          segments[--i] = p.getSegmentValue();
        }

        // Travel down from the root to the requested node, creating child nodes along the way
        // as needed.
        Node current = root;
        for (i = 0; i < depth; i++) {
          Object segment = segments[i];
          Node child = current.childrenBySegment.get(segment);
          if (child == null) {
            Reports.Trace.Node.Builder childBuilder = Reports.Trace.Node.newBuilder();
            if (segment instanceof Integer) {
              childBuilder.setIndex((Integer) segment);
            } else if (i < depth - 1) {
              // We've encountered a field name node that is an ancestor of the requested
              // node. However, the fetcher for that field name should have ultimately
              // called getOrCreateNode() on its node before getOrCreateNode() was called
              // on the requested node, meaning that this field name node should already
              // be in the trie. Accordingly, we should never encounter such field name
              // ancestor nodes, and we throw when we do.
              throw new RuntimeException("Unexpected missing non-index " + segment);
            }
            Node childNode = new Node(childBuilder);

            // Note that putIfAbsent() here will give the child node if it already existed,
            // or null if it didn't (in which case the node passed to putIfAbsent() becomes
            // the child node). Only the thread that inserted the node adds it to the
            // parent's ordered children list, so each node appears there exactly once.
            child = current.childrenBySegment.putIfAbsent(segment, childNode);
            if (child == null) {
              child = childNode;
              current.children.add(childNode);
            }
          }
          current = child;
        }

        return current;
      }

      /**
       * Convert the entire builder tree to protobuf, forming the parent-child relationships as
       * needed.
       *
       * <p>Note that no builder may be edited after this method is called. This method is
       * synchronized only so that the one-time conversion is idempotent if it is ever called
       * concurrently; it is off the per-field hot path.
       */
      @NotNull
      public synchronized Reports.Trace.Node toProto() {
        if (!finalized) {
          buildDescendants(root);
          finalized = true;
        }
        return root.builder.build();
      }

      /**
       * Recursively build the protobuf builder for the given node, forming the parent-child
       * relationships between builders of descendant nodes as needed.
       */
      private void buildDescendants(Node node) {
        for (Node childNode : node.children) {
          buildDescendants(childNode);
          node.builder.addChild(childNode.builder.build());
        }
      }

      private static class Node {
        public final Reports.Trace.Node.Builder builder;
        // Children keyed by result path segment value (String for fields, Integer for list
        // indices), used for lookup.
        public final ConcurrentMap<Object, Node> childrenBySegment;
        // Children in insertion order, used for protobuf output.
        public final ConcurrentLinkedQueue<Node> children;

        public Node(Reports.Trace.Node.Builder builder) {
          this.builder = builder;
          this.childrenBySegment = new ConcurrentHashMap<>();
          this.children = new ConcurrentLinkedQueue<>();
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
