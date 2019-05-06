package com.apollographql.federation.examples.spring;

import com.apollographql.federation.Federation;
import com.apollographql.federation.FederationDirectives;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    final Logger logger = LoggerFactory.getLogger(App.class);

    @Bean
    GraphQLSchema schema(@Value("classpath:schema.graphql") Resource sdl) throws IOException {
        // Load our schema definition from resources
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(
                new String(Files.readAllBytes(sdl.getFile().toPath())));

        // Declare Apollo Federation directives (we use @key)
        typeRegistry.addAll(FederationDirectives.allDefinitions);

        final GraphQLSchema base = new SchemaGenerator()
                .makeExecutableSchema(typeRegistry, RuntimeWiring.newRuntimeWiring()
                        .build());

        return Federation.transform(base)
                .fetchEntities(env -> {
                    return null;
                })
                .resolveEntityType(env -> {
                    return null;
                })
                .build();
    }
}
