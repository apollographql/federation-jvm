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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            List<Object> pathParts = path.toList();
            return nodesByPath.computeIfAbsent(ExecutionPath.fromList(pathParts.subList(0, pathParts.size() - 1)), parentPath -> {
                if (parentPath.equals(ExecutionPath.rootPath())) {
                    // The root path is inserted at construction time, so this shouldn't happen.
                    throw new RuntimeException("root path missing from nodesByPath?");
                }

                // Recursively get the grandparent node and start building the parent node.
                Reports.Trace.Node.Builder missingParent = getParentNode(parentPath).addChildBuilder();

                // If the parent was a field name, then its fetcher would have been called before
                // the fetcher for 'path' and it would be in nodesByPath. So the parent must be
                // a list index.  Note that we subtract 2 here because we want the last part of
                // parentPath, not path.
                Object parentLastPathPart = pathParts.get(pathParts.size() - 2);
                if (!(parentLastPathPart instanceof Integer)) {
                    throw new RuntimeException("Unexpected missing non-index " + parentLastPathPart);
                }
                missingParent.setIndex((Integer) parentLastPathPart);
                return missingParent;
            });
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
