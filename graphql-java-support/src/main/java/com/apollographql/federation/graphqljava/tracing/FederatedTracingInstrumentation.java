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
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.validation.ValidationError;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
        return whenCompleted((document, throwable) -> {
            for (GraphQLError error : convertErrors(throwable, null)) {
                state.addRootError(error);
            }
        });
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        FederatedTracingState state = parameters.getInstrumentationState();
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
                GraphqlErrorBuilder errorBuilder = GraphqlErrorBuilder.newError()
                        .message(throwable.getMessage());

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
            Reports.Trace.Node.Builder parent = getParentNode(path);

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
        Reports.Trace.Node.Builder getParentNode(ExecutionPath path) {
            List<Object> pathList = path.toList();
            List<Integer> missingIndexes = new ArrayList<>();
            Reports.Trace.Node.Builder ancestorNode;
            while (true) {
                if (pathList.isEmpty()) {
                    throw new RuntimeException("Didn't find any ancestor, even root?");
                }
                pathList.remove(pathList.size() - 1);
                ancestorNode = nodesByPath.get(ExecutionPath.fromList(pathList));
                if (ancestorNode != null) {
                    break;
                }
                // This ancestor level hasn't had a field fetch call, so it must be a list index
                // rather than a field name.
                Object lastElement = pathList.get(pathList.size() - 1);
                if (!(lastElement instanceof Integer)) {
                    throw new RuntimeException("Unexpected missing non-index " + lastElement);
                }
                missingIndexes.add((Integer) lastElement);
            }

            // We may have had some missing intermediate nodes, so create them all.
            // We added the most deeply nested indexes first, so reverse before we iterate. (This
            // list is almost always size 0 or 1 where this is a no-op.)
            Collections.reverse(missingIndexes);
            for (Integer missingIndex : missingIndexes) {
                ancestorNode = ancestorNode.addChildBuilder().setIndex(missingIndex);
                pathList.add(missingIndex);
                nodesByPath.put(ExecutionPath.fromList(pathList), ancestorNode);
            }

            return ancestorNode;
        }

        void addRootError(GraphQLError error) {
            Reports.Trace.Error.Builder builder = nodesByPath.get(ExecutionPath.rootPath()).addErrorBuilder()
                    .setMessage(error.getMessage());

            error.getLocations().forEach(location -> builder.addLocationBuilder()
                    .setColumn(location.getColumn())
                    .setLine(location.getLine()));
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
