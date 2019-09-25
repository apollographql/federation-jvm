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
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.SourceLocation;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

public class FederatedTracingInstrumentation extends SimpleInstrumentation {
    private static final String EXTENSION_KEY = "ftv1";
    private final Options options;

    private static final Logger logger = LoggerFactory.getLogger(FederatedTracingInstrumentation.class);

    public FederatedTracingInstrumentation() {
        this.options = Options.newOptions();
    }

    public FederatedTracingInstrumentation(Options options) {
        this.options = options;
    }

    @Override
    public InstrumentationState createState() {
        return new FederatedTracingState();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        Reports.Trace trace = parameters.<FederatedTracingState>getInstrumentationState().toProto();

        if (options.isDebuggingEnabled()) {
            logger.debug(trace.toString());
        }

        return CompletableFuture.completedFuture(new ExecutionResultImpl.Builder()
                .from(executionResult)
                .addExtension(EXTENSION_KEY, Base64.getEncoder().encodeToString(trace.toByteArray()))
                .build());
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
        SourceLocation fieldLocation = parameters.getEnvironment().getField().getSourceLocation();

        long startNanos = System.nanoTime();
        return whenCompleted((result, throwable) -> {
            long endNanos = System.nanoTime();

            ExecutionStepInfo executionStepInfo = parameters.getEnvironment().getExecutionStepInfo();
            state.addNode(
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

    // Field resolvers can throw exceptions or add errors to the DataFetchingResult. This method normalizes them to a
    // single list of GraphQLErrors.
    @NotNull
    private List<GraphQLError> convertErrors(Throwable throwable, Object result) {
        ArrayList<GraphQLError> graphQLErrors = new ArrayList<>();

        if (throwable != null) {
            if (throwable instanceof GraphQLError) {
                graphQLErrors.add((GraphQLError) throwable);
            } else {
                graphQLErrors.add(GraphqlErrorBuilder.newError()
                        .message(throwable.getMessage())
                        .build());
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
        private final LinkedHashMap<ExecutionPath, Reports.Trace.Node.Builder> nodesByPath;

        private FederatedTracingState() {
            // record start time when creating instrumentation state for a request
            startRequestTime = Instant.now();
            startRequestNanos = System.nanoTime();

            nodesByPath = new LinkedHashMap<>();
            nodesByPath.put(ExecutionPath.rootPath(), Reports.Trace.Node.newBuilder());
        }

        @NotNull
        Reports.Trace toProto() {
            return Reports.Trace.newBuilder()
                    .setStartTime(getStartTimestamp())
                    .setEndTime(getNowTimestamp())
                    .setDurationNs(getDuration())
                    .setRoot(nodesByPath.get(ExecutionPath.rootPath()))
                    .build();
        }

        /**
         * Adds node to nodesByPath and recursively ensures that all parent nodes exist.
         */
        void addNode(ExecutionStepInfo stepInfo, long startFieldNanos, long endFieldNanos, List<GraphQLError> errors, SourceLocation fieldLocation) {
            ExecutionPath path = stepInfo.getPath();
            Reports.Trace.Node.Builder parent = getParent(path);

            Reports.Trace.Node.Builder node = parent.addChildBuilder()
                    .setStartTime(startFieldNanos)
                    .setEndTime(endFieldNanos)
                    .setParentType(simplePrint(stepInfo.getParent().getUnwrappedNonNullType()))
                    .setType(stepInfo.simplePrint())
                    .setResponseName(stepInfo.getResultKey());

            String originalFieldName = stepInfo.getField().getName();

            // set originalFieldName only when a field alias was used
            if (!originalFieldName.equals(stepInfo.getResultKey())) {
                node.setOriginalFieldName(originalFieldName);
            }

            errors.forEach(error -> {
                Reports.Trace.Error.Builder builder = node.addErrorBuilder().setMessage(error.getMessage());
                if (error.getLocations().isEmpty() && fieldLocation != null) {
                    builder.addLocationBuilder()
                            .setColumn(fieldLocation.getColumn())
                            .setLine(fieldLocation.getLine());
                } else {
                    error.getLocations().forEach(location -> builder.addLocationBuilder()
                            .setColumn(location.getColumn())
                            .setLine(location.getLine()));
                }
            });

            nodesByPath.put(path, node);
        }

        @NotNull
        Reports.Trace.Node.Builder getParent(ExecutionPath path) {
            List<Object> pathParts = path.toList();
            ExecutionPath parentPath = ExecutionPath.fromList(pathParts.subList(0, pathParts.size() - 1));

            if (!nodesByPath.containsKey(parentPath)) {
                // This recurses to support nested lists. It will eventually find the root node
                // created in the constructor.
                Reports.Trace.Node.Builder missingParent = getParent(parentPath).addChildBuilder();

                // Missing parents are always list items, so we need to add the `index` field to them.
                // There isn't an instrumentation hook for list items, but we need a node in the trace
                // tree to contain the fields in the list item. This doesn't apply to scalar values, as
                // `beginFieldFetch` isn't called for those list items.
                if (parentPath.getLevel() > 0) {
                    Object lastPathPart = parentPath.toList().get(parentPath.getLevel());
                    if (lastPathPart instanceof Number) { // will always be true
                        int index = ((Number) lastPathPart).intValue();
                        missingParent.setIndex(index);
                    }
                }

                nodesByPath.put(parentPath, missingParent);
                return missingParent;
            }

            return nodesByPath.get(parentPath);
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
