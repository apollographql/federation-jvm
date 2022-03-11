package com.apollographql.federation.graphqljava;

import graphql.Scalars;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;

public class link__Import {
  static final String typeName = "link__Import";

  public static GraphQLScalarType type =
      GraphQLScalarType.newScalar()
          .name(typeName)
          .description(null)
          .coercing(Scalars.GraphQLString.getCoercing())
          .build();

  public static final ScalarTypeDefinition definition =
      ScalarTypeDefinition.newScalarTypeDefinition().name(typeName).build();
}
