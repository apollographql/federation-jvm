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

/**
 * Some tips for running with graphql-java-tools:
 *
 * 1. You may find that graphql-java-tools removes types that are only used by the federation
 *    _entities field. This is because graphql-java-tools performs these optimizations before
 *    federation-graphql-java-support can alter the schema to include the _entities field. The
 *    simplest workaround here is to both (1) add the type to the {@link SchemaParserDictionary}
 *    bean and to (2) set the {@link SchemaParserOptions.Builder} bean to have includeUnusedTypes
 *    as true. However, the latter can't be done via properties.xml/yml since the getters/setters
 *    aren't named according to the JavaBeans API specification (i.e. getFoo()/setFoo()). You can
 *    work around this by providing the whole bean via code, or using a {@link BeanPostProcessor}
 *    as shown below to customize the auto-configuration bean.
 *
 * 2. If you have an empty query type, you don't need to add a dummy field to your
 *    {@link graphql.kickstart.tools.GraphQLQueryResolver} or to your schema string. However, you
 *    do need to (1) declare an empty {@link graphql.kickstart.tools.GraphQLQueryResolver}, (2)
 *    declare an empty "type Query" in your schema string, and (3) setup {@link SchemaTransformer}
 *    as shown below. We could potentially modify the federation-graphql-java-support API to accept
 *    {@link GraphQLSchema.Builder}s, but unfortunately those builders have no public getters, so
 *    we wouldn't be able to copy their data.
 */
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
