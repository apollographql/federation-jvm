package com.apollographql.federation.springexample.graphqljavatools;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava.SchemaTransformer;
import graphql.Scalars;
import graphql.kickstart.tools.SchemaObjects;
import graphql.kickstart.tools.SchemaParser;
import graphql.kickstart.tools.SchemaParserDictionary;
import graphql.kickstart.tools.SchemaParserOptions;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLJavaToolsConfiguration {
    @Bean
    public SchemaParserDictionary schemaParserDictionary() {
        return new SchemaParserDictionary().add("Product", Product.class);
    }

    @Bean
    public BeanPostProcessor schemaParserOptionsBuilderPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
                return bean instanceof SchemaParserOptions.Builder
                        ? ((SchemaParserOptions.Builder) bean).includeUnusedTypes(true)
                        : bean;
            }
        };
    }

    @Bean
    public SchemaTransformer schemaTransformer(SchemaParser schemaParser) {
        final SchemaObjects schemaObjects = schemaParser.parseSchemaObjects();
        final boolean queryTypeIsEmpty = schemaObjects.getQuery().getFieldDefinitions().isEmpty();
        final GraphQLObjectType newQuery = queryTypeIsEmpty
                ? schemaObjects.getQuery().transform(graphQLObjectTypeBuilder ->
                        graphQLObjectTypeBuilder.field(GraphQLFieldDefinition.newFieldDefinition()
                                .name("_dummy")
                                .type(Scalars.GraphQLString)
                                .build()
                        )
                )
                : schemaObjects.getQuery();
        final GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
                .query(newQuery)
                .mutation(schemaObjects.getMutation())
                .subscription(schemaObjects.getSubscription())
                .additionalTypes(schemaObjects.getDictionary())
                .codeRegistry(schemaObjects.getCodeRegistryBuilder().build())
                .build();
        return Federation.transform(graphQLSchema, queryTypeIsEmpty);
    }
}
