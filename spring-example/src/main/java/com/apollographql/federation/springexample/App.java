package com.apollographql.federation.springexample;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import graphql.schema.GraphQLSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public GraphQLSchema graphQLSchema(@Value("classpath:schemas/inventory.graphql") Resource sdl) throws IOException {
        return Federation.transform(sdl.getFile())
                .fetchEntities(env -> env.<List<Map<String, Object>>>getArgument(_Entity.argumentName)
                        .stream()
                        .map(reference -> {
                            if ("Product".equals(reference.get("__typename"))) {
                                return Product.resolveReference(reference);
                            }
                            return null;
                        })
                        .collect(Collectors.toList()))
                .resolveEntityType(env -> {
                    final Object src = env.getObject();
                    if (src instanceof Product) {
                        return env.getSchema().getObjectType("Product");
                    }
                    return null;
                })
                .build();
    }

    @Bean
    public FederatedTracingInstrumentation federatedTracingInstrumentation() {
        return new FederatedTracingInstrumentation(new FederatedTracingInstrumentation.Options(true));
    }
}
