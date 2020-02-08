package io.gqljf.federation;

import graphql.schema.*;
import graphql.schema.idl.*;
import io.gqljf.federation.misc.Constants;
import io.gqljf.federation.misc.FederationException;
import io.gqljf.federation.misc.Utils;

import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class FederatedSchemaBuilder {

    private final Utils utils = new Utils();
    private final List<String> apolloServerExistedDirectives = List.of("include", "skip", "deprecated");
    private InputStream schemaInputStream;
    private RuntimeWiring runtimeWiring;
    /**
     * Subscriptions are excluded from the federated schema because of https://github.com/apollographql/apollo-server/issues/3357
     * Subscriptions still can be used in a standalone application
     */
    private boolean excludeSubscriptionsFromApolloSdl = false;
    private List<FederatedEntityResolver<?, ?>> federatedEntityResolvers;
    private GraphQLSchema originalSchema;

    public FederatedSchemaBuilder schemaInputStream(InputStream schemaInputStream) {
        this.schemaInputStream = schemaInputStream;
        return this;
    }

    public FederatedSchemaBuilder runtimeWiring(RuntimeWiring runtimeWiring) {
        this.runtimeWiring = runtimeWiring;
        return this;
    }

    public FederatedSchemaBuilder excludeSubscriptionsFromApolloSdl(boolean excludeSubscriptionsFromApolloSdl) {
        this.excludeSubscriptionsFromApolloSdl = excludeSubscriptionsFromApolloSdl;
        return this;
    }

    public FederatedSchemaBuilder federatedEntitiesResolvers(final List<FederatedEntityResolver<?, ?>> federatedEntityResolvers) {
        this.federatedEntityResolvers = Collections.unmodifiableList(federatedEntityResolvers);
        return this;
    }

    public final GraphQLSchema build() {
        this.originalSchema = createOriginalSchema(this.schemaInputStream, this.runtimeWiring);

        final GraphQLObjectType originalQueryType = originalSchema.getQueryType();
        final GraphQLSchema.Builder newSchema = GraphQLSchema.newSchema(originalSchema);
        final GraphQLObjectType.Builder newQueryType = GraphQLObjectType
                .newObject(originalQueryType)
                .field(utils.createServiceField());
        final GraphQLCodeRegistry.Builder newCodeRegistry = createNewCodeRegistry(originalQueryType);
        final Set<String> entityConcreteTypeNames = getEntityConcreteTypeNames();

        if (!entityConcreteTypeNames.isEmpty()) {
            newQueryType.field(utils.createFieldDefinition(entityConcreteTypeNames));

            final GraphQLType originalAnyType = originalSchema.getType(Constants.ANY_TYPE_NAME);
            if (originalAnyType == null) {
                newSchema.additionalType(utils.createAnyType());
            }

            final FieldCoordinates _entities = FieldCoordinates.coordinates(originalQueryType.getName(), Constants.ENTITY_FIELD_NAME);
            newCodeRegistry.dataFetcher(_entities, createDataFetcher());
            newCodeRegistry.typeResolver(Constants.ENTITY_TYPE_NAME, createTypeResolver());
        }

        return newSchema
                .query(newQueryType.build())
                .codeRegistry(newCodeRegistry.build())
                .build();
    }

    private GraphQLSchema createOriginalSchema(final InputStream sdl, final RuntimeWiring runtimeWiring) {
        if (sdl == null) {
            throw new FederationException("SDL should not be null");
        }

        if (runtimeWiring == null) {
            throw new FederationException("RuntimeWiring should not be null");
        }

        final TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
        final SchemaGenerator.Options options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false);
        return new SchemaGenerator().makeExecutableSchema(
                options,
                typeDefinitionRegistry,
                runtimeWiring
        );
    }

    private Set<String> getEntityConcreteTypeNames() {
        final Set<GraphQLNamedType> entityTypeNames = originalSchema.getAllTypesAsList().stream()
                .filter(t -> t instanceof GraphQLDirectiveContainer && ((GraphQLDirectiveContainer) t).getDirective(Constants.KEY_DIRECTIVE_NAME) != null)
                .collect(Collectors.toSet());

        final Predicate<GraphQLObjectType> isConcreteEntityType = objectType -> entityTypeNames.contains(objectType) ||
                objectType.getInterfaces().stream().anyMatch(entityTypeNames::contains);

        return originalSchema.getAllTypesAsList()
                .stream()
                .filter(type -> type instanceof GraphQLObjectType)
                .map(objectType -> (GraphQLObjectType) objectType)
                .filter(isConcreteEntityType)
                .map(GraphQLNamedType::getName)
                .collect(Collectors.toSet());
    }

    // todo batch loading
    private DataFetcher<?> createDataFetcher() {
        return env -> {
            final List<Map<String, Object>> representations = env.getArgument(Constants.REPRESENTATIONS_ARGUMENT_NAME);

            return representations.stream()
                    .map(representation -> {
                        final String typeName = representation.get(Constants.TYPENAME_FIELD_NAME).toString();
                        final FederatedEntityResolver matchedResolver = findFederatedEntityResolver(federatedEntityResolver -> typeName.equals(federatedEntityResolver.getTypeName()));
                        final Object federatedEntityId = getFederatedEntityId(representation, typeName);
                        return matchedResolver.getEntity(federatedEntityId);
                    })
                    .collect(Collectors.toList());
        };
    }

    private TypeResolver createTypeResolver() {
        return env -> {
            final Object source = env.getObject();
            final FederatedEntityResolver<?, ?> federatedEntityResolver = findFederatedEntityResolver(fer -> fer.getEntityClass() == source.getClass());
            return env.getSchema().getObjectType(federatedEntityResolver.getTypeName());
        };
    }

    private FederatedEntityResolver<?, ?> findFederatedEntityResolver(Predicate<FederatedEntityResolver<?, ?>> predicate) {
        return this.federatedEntityResolvers.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new FederationException("Can't find FederatedEntityResolver"));
    }

    private Object getFederatedEntityId(Map<String, ?> representation, String typeName) {
        final GraphQLDirective keyDirective = originalSchema.getType(typeName).getChildren().stream()
                .filter(e -> e instanceof GraphQLDirective)
                .map(e -> (GraphQLDirective) e)
                .filter(e -> Constants.KEY_DIRECTIVE_NAME.equals(e.getName()))
                .findFirst()
                .orElseThrow(() -> new FederationException("Can't find @key directive"));

        final String idFieldName = keyDirective.getArguments().get(0).getValue().toString();

        final String idValue = (String) representation.get(idFieldName);

        final FederatedEntityResolver<?, ?> matchedResolver = findFederatedEntityResolver(federatedEntityResolver -> typeName.equals(federatedEntityResolver.getTypeName()));

        final Class idFieldClass = matchedResolver.getIdClass();
        // if type of your `id` field is not properly processed, please create an issue
        if (idFieldClass == Long.class) {
            return Long.parseLong(idValue);
        } else if (idFieldClass == Integer.class) {
            return Integer.parseInt(idValue);
        } else {
            return idValue;
        }
    }

    private GraphQLCodeRegistry.Builder createNewCodeRegistry(GraphQLObjectType originalQueryType) {
        final Object dummy = new Object();
        return GraphQLCodeRegistry
                .newCodeRegistry(originalSchema.getCodeRegistry())
                .dataFetcher(FieldCoordinates.coordinates(originalQueryType.getName(), Constants.SERVICE_FIELD_NAME), (DataFetcher<Object>) environment -> dummy)
                .dataFetcher(FieldCoordinates.coordinates(Constants.SERVICE_TYPE_NAME, Constants.SDL_FIELD_NAME), (DataFetcher<String>) environment -> getSchema());
    }

    private String getSchema() {
        final SchemaPrinter.Options options = SchemaPrinter.Options.defaultOptions()
                .includeScalarTypes(true)
                .includeExtendedScalarTypes(true)
                .includeSchemaDefinition(true)
                .includeDirectives(d -> !apolloServerExistedDirectives.contains(d.getName()));
        String schema = new SchemaPrinter(options).print(originalSchema);
        if (this.excludeSubscriptionsFromApolloSdl) {
            schema = removeSubscriptionDefinitionIfExists(schema);
        }
        return schema;
    }

    private String removeSubscriptionDefinitionIfExists(final String schema) {
        final int subscriptionDefinitionStartIndex = schema.indexOf("type Subscription");
        // checks whether the schema contains subscription type at all
        if (subscriptionDefinitionStartIndex != -1) {
            final String tempString = schema.substring(subscriptionDefinitionStartIndex);
            final int subscriptionDefinitionLength = tempString.indexOf("}") + 1;
            final String subscriptionDefinition = schema.substring(subscriptionDefinitionStartIndex, subscriptionDefinitionStartIndex + subscriptionDefinitionLength);
            return schema.replace(subscriptionDefinition, "");
        }
        return schema;
    }
}
