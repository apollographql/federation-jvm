package com.apollographql.federation.springexample;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava.FederationDirectives;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
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

        // Build the base schema, which includes directives like @base and @extends
        final GraphQLSchema baseSchema = new SchemaGenerator()
                .makeExecutableSchema(typeRegistry, RuntimeWiring.newRuntimeWiring()
                        .build());

        if (logger.isDebugEnabled()) {
            logger.debug("Base schema: {}", baseSchema);
        }

        // Transform the schema to add federation support.
        // It exposes the IDL of the base schema in `query{_service{idl}}`
        // and adds entity support.
        final GraphQLSchema federatedSchema =
                Federation.transform(baseSchema)
                        .fetchEntities(env -> null)
                        .resolveEntityType(env -> null)
                        .build();

        if (logger.isDebugEnabled()) {
            logger.debug("Federated schema: {}", federatedSchema);
        }

        return federatedSchema;
    }

    private String print(final GraphQLSchema schema) {
        return new SchemaPrinter().print(schema);
    }
}
