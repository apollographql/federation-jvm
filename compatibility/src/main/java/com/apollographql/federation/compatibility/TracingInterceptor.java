package com.apollographql.federation.compatibility;

import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FEDERATED_TRACING_HEADER_NAME;

@Component
public class TracingInterceptor implements WebGraphQlInterceptor {

    @Override
    public @NotNull Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, @NotNull Chain chain) {
        String headerValue = request.getHeaders().getFirst(FEDERATED_TRACING_HEADER_NAME);
        if (headerValue != null) {
            request.configureExecutionInput((executionInput, builder) ->
                    builder.graphQLContext(Collections.singletonMap(FEDERATED_TRACING_HEADER_NAME, headerValue)).build());
        }
        return chain.next(request);
    }
}