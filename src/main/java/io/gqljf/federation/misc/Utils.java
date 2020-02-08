package io.gqljf.federation.misc;

import graphql.Scalars;
import graphql.schema.*;

import java.util.Set;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLScalarType.newScalar;

public final class Utils {

    // graphql-java will mutate GraphQLTypeReference in-place,
    // so we need to create a new instance every time.
    public GraphQLFieldDefinition createFieldDefinition(final Set<String> typeNames) {
        return newFieldDefinition()
                .name(Constants.ENTITY_FIELD_NAME)
                .argument(newArgument()
                        .name(Constants.REPRESENTATIONS_ARGUMENT_NAME)
                        .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference(Constants.ANY_TYPE_NAME)))))
                        .build())
                .type(new GraphQLNonNull(new GraphQLList(createUnionType(typeNames))))
                .build();
    }

    public GraphQLScalarType createAnyType() {
        return newScalar()
                .name(Constants.ANY_TYPE_NAME)
                .coercing(new AnyApolloTypeCoercing())
                .build();
    }

    public GraphQLFieldDefinition createServiceField() {
        return newFieldDefinition()
                .name(Constants.SERVICE_FIELD_NAME)
                .type(createServiceType())
                .build();
    }

    private GraphQLUnionType createUnionType(final Set<String> typeNames) {
        return GraphQLUnionType.newUnionType()
                .name(Constants.ENTITY_TYPE_NAME)
                .possibleTypes(typeNames.stream()
                        .map(GraphQLTypeReference::new)
                        .toArray(GraphQLTypeReference[]::new))
                .build();
    }

    private GraphQLObjectType createServiceType() {
        return newObject()
                .name(Constants.SERVICE_TYPE_NAME)
                .field(newFieldDefinition()
                        .name(Constants.SDL_FIELD_NAME)
                        .type(new GraphQLNonNull(Scalars.GraphQLString))
                        .build())
                .build();
    }
}
