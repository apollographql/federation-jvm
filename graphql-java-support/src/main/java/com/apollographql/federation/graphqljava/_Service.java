package com.apollographql.federation.graphqljava;

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;

public final class _Service {
  public static final String typeName = "_Service";
  static final String fieldName = "_service";
  static final String sdlFieldName = "sdl";

  static final GraphQLObjectType type =
      newObject()
          .name(typeName)
          .field(
              newFieldDefinition()
                  .name(sdlFieldName)
                  .type(new GraphQLNonNull(Scalars.GraphQLString))
                  .build())
          .build();

  static final GraphQLFieldDefinition field =
      newFieldDefinition().name(fieldName)
        .type(GraphQLNonNull.nonNull(type))
        .build();

  private _Service() {}
}
