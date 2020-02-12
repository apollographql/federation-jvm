package com.apollographql.federation.graphqljava.tracing;

import com.google.protobuf.Timestamp;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.validation.ValidationError;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

public class FederatedTracingInstrumentation extends SimpleInstrumentation {
    private static final String EXTENSION_KEY = "ftv1";
    private static final String HEADER_NAME = "apollo-federation-include-trace";
    private static final String HEADER_VALUE = "ftv1";

    private final Options options;

    private static final Logger logger = LoggerFactory.getLogger(FederatedTracingInstrumentation.class);

    public FederatedTracingInstrumentation() {
        this.options = Options.newOptions();
    }

    public FederatedTracingInstrumentation(Options options) {
        this.options = options;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        // If we've been configured with a way of reading HTTP headers, we should only be active
        // if the special HTTP header has the special value. If the header isn't provided or has
        // a different value, return null for our state, which we'll interpret in the rest of this
        // file as meaning "don't instrument".  (If we haven't been given access to HTTP headers,
        // always instrument.)
        Object context = parameters.getExecutionInput().getContext();
        if (context instanceof HTTPRequestHeaders) {
            @Nullable String headerValue = ((HTTPRequestHeaders) context).getHTTPRequestHeader(HEADER_NAME);
            if (headerValue == null || !headerValue.equals(HEADER_VALUE)) {
                return null;
            }
        }
        return new FederatedTracingState();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        final @Nullable FederatedTracingState state = parameters.<FederatedTracingState>getInstrumentationState();
        if (state == null) {
            return super.instrumentExecutionResult(executionResult, parameters);
        }

        Reports.Trace trace = state.toProto();

        if (options.isDebuggingEnabled()) {
            logger.debug(trace.toString());
        }

        // Elaborately copy the result into a builder.
        // Annoyingly, ExecutionResultImpl.Builder.from takes ExecutionResultImpl rather than
        // ExecutionResult in versions of GraphQL-Java older than v13
        // (see https://github.com/graphql-java/graphql-java/pull/1491), so to support older versions
        // we copy the fields by hand, which does result in isDataPresent always being set (ie,
        // "data": null being included in all results). The built-in TracingInstrumentation has
        // the same issue. If we decide to only support v13 then this can just change to
        // ExecutionResultImpl.newExecutionResult().from(executionResult).
        return CompletableFuture.completedFuture(ExecutionResultImpl.newExecutionResult()
                .data(executionResult.getData())
                .errors(executionResult.getErrors())
                .extensions(executionResult.getExtensions())
                .addExtension(EXTENSION_KEY, Base64.getEncoder().encodeToString(trace.toByteArray()))
                .build());
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
        if (state == null) {
            return super.beginFieldFetch(parameters);
        }

        SourceLocation fieldLocation = parameters.getEnvironment().getField().getSourceLocation();

        long startNanos = System.nanoTime();
        return whenCompleted((result, throwable) -> {
            long endNanos = System.nanoTime();

            ExecutionStepInfo executionStepInfo = parameters.getEnvironment().getExecutionStepInfo();
            state.addFieldFetchData(
                    executionStepInfo,
                    // relative to the trace's start_time, in ns
                    startNanos - state.getStartRequestNanos(),
                    // relative to the trace's start_time, in ns
                    endNanos - state.getStartRequestNanos(),
                    convertErrors(throwable, result),
                    fieldLocation
            );
        });
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
        if (state == null) {
            return super.beginParse(parameters);
        }

        return whenCompleted((document, throwable) -> {
            for (GraphQLError error : convertErrors(throwable, null)) {
                state.addRootError(error);
            }
        });
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
        if (state == null) {
            return super.beginValidation(parameters);
        }

        return whenCompleted((validationErrors, throwable) -> {
            for (GraphQLError error : convertErrors(throwable, null)) {
                state.addRootError(error);
            }

            for (ValidationError error : validationErrors) {
                state.addRootError(error);
            }
        });
    }

    // Field resolvers can throw exceptions or add errors to the DataFetchingResult. This method normalizes them to a
    // single list of GraphQLErrors.
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
                GraphqlErrorBuilder errorBuilder = GraphqlErrorBuilder.newError()
                        .message(message);

                if (throwable instanceof InvalidSyntaxException) {
                    errorBuilder.location(((InvalidSyntaxException) throwable).getLocation());
                }

                graphQLErrors.add(errorBuilder.build());
            }
        }

        if (result instanceof DataFetcherResult<?>) {
            DataFetcherResult<?> theResult = (DataFetcherResult) result;
            if (theResult.hasErrors()) {
                graphQLErrors.addAll(theResult.getErrors());
            }
        }

        return graphQLErrors;
    }

    /**
     * Stores timing information and a map of nodes for field information.
     */
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

        /**
         * Adds stats data collected from a field fetch.
         */
        void addFieldFetchData(ExecutionStepInfo stepInfo, long startFieldNanos, long endFieldNanos, List<GraphQLError> errors, SourceLocation fieldLocation) {
            ExecutionPath path = stepInfo.getPath();
            protoBuilderTree.editBuilder(path, (builder) -> {
                builder.setStartTime(startFieldNanos)
                        .setEndTime(endFieldNanos)
                        .setParentType(simplePrint(stepInfo.getParent().getUnwrappedNonNullType()))
                        .setType(stepInfo.simplePrint())
                        .setResponseName(stepInfo.getResultKey());

                // set originalFieldName only when a field alias was used
                String originalFieldName = stepInfo.getField().getName();
                if (!originalFieldName.equals(stepInfo.getResultKey())) {
                    builder.setOriginalFieldName(originalFieldName);
                }

                errors.forEach(error -> {
                    Reports.Trace.Error.Builder errorBuilder = builder.addErrorBuilder()
                            .setMessage(error.getMessage());
                    if (error.getLocations().isEmpty() && fieldLocation != null) {
                        errorBuilder.addLocationBuilder()
                                .setColumn(fieldLocation.getColumn())
                                .setLine(fieldLocation.getLine());
                    } else {
                        error.getLocations().forEach(location -> errorBuilder.addLocationBuilder()
                                .setColumn(location.getColumn())
                                .setLine(location.getLine()));
                    }
                });
            });
        }

        void addRootError(GraphQLError error) {
            protoBuilderTree.editBuilder(ExecutionPath.rootPath(), (builder) -> {
                Reports.Trace.Error.Builder errorBuilder = builder.addErrorBuilder()
                        .setMessage(error.getMessage());

                error.getLocations().forEach(location -> errorBuilder.addLocationBuilder()
                        .setColumn(location.getColumn())
                        .setLine(location.getLine()));
            });
        }

        long getStartRequestNanos() {
            return startRequestNanos;
        }

        @NotNull
        private static Timestamp instantToTimestamp(@NotNull Instant startRequestTime2) {
            return Timestamp.newBuilder()
                    .setSeconds(startRequestTime2.getEpochSecond())
                    .setNanos(startRequestTime2.getNano()).build();
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

        /**
         * A thread-safe class for storing field information and converting it to a protobuf.
         */
        private static class ProtoBuilderTree {
            private final Node root;
            private final ConcurrentMap<ExecutionPath, Node> nodesByPath;
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
                nodesByPath.put(ExecutionPath.rootPath(), root);
                treeLock = new ReentrantReadWriteLock();
            }

            /**
             * Edit builder for the node at the given path (creating it and its parents if needed).
             */
            public void editBuilder(ExecutionPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
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
             * Note that {@link #treeLock}'s read lock must be held when calling this method.
             */
            @NotNull
            private Node getOrCreateNode(ExecutionPath path) {
                // Fast path for when the node already exists.
                Node current = nodesByPath.get(path);
                if (current != null) return current;

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
                    ExecutionPath currentPath = ExecutionPath.fromList(pathSegments.subList(0, currentSegmentIndex));
                    current = nodesByPath.get(currentPath);
                }

                // Travel back down to the requested node, creating child nodes along the way as
                // needed.
                for (; currentSegmentIndex < pathSegments.size(); currentSegmentIndex++) {
                    Node parent = current;
                    ExecutionPath childPath = ExecutionPath.fromList(pathSegments.subList(0, currentSegmentIndex + 1));
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
             * Convert the entire builder tree to protobuf, forming the parent-child relationships
             * as needed.
             *
             * Note that no builder may be edited after this method is called.
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
             * Note that {@link #treeLock}'s write lock must be held when calling this method.
             */
            private void buildDescendants(Node node) {
                for (Node childNode: node.children) {
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

    public static class Options {
        private final boolean debuggingEnabled;

        public Options(boolean debuggingEnabled) {
            this.debuggingEnabled = debuggingEnabled;
        }

        public static @NotNull Options newOptions() {
            return new Options(false);
        }

        public boolean isDebuggingEnabled() {
            return debuggingEnabled;
        }
    }
}
