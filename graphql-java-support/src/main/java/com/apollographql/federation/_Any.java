package com.apollographql.federation;

import graphql.Assert;
import graphql.language.*;
import graphql.schema.*;

import java.util.stream.Collectors;

import static graphql.schema.GraphQLScalarType.newScalar;

final class _Any {
    static final String typeName = "_Any";

    static GraphQLScalarType type = newScalar()
            .name(typeName)
            .coercing(new Coercing() {
                @Override
                public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    return dataFetcherResult;
                }

                @Override
                public Object parseValue(Object input) throws CoercingParseValueException {
                    return input;
                }

                @Override
                public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof NullValue) {
                        return null;
                    } else if (input instanceof FloatValue) {
                        return ((FloatValue) input).getValue();
                    } else if (input instanceof StringValue) {
                        return ((StringValue) input).getValue();
                    } else if (input instanceof IntValue) {
                        return ((IntValue) input).getValue();
                    } else if (input instanceof BooleanValue) {
                        return ((BooleanValue) input).isValue();
                    } else if (input instanceof EnumValue) {
                        return ((EnumValue) input).getName();
                    } else if (input instanceof ArrayValue) {
                        return ((ArrayValue) input).getValues()
                                .stream()
                                .map(this::parseLiteral)
                                .collect(Collectors.toList());
                    } else if (input instanceof ObjectValue) {
                        return ((ObjectValue) input).getObjectFields()
                                .stream()
                                .collect(Collectors.toMap(ObjectField::getName, f -> parseLiteral(f.getValue())));
                    }
                    return Assert.assertShouldNeverHappen();
                }
            })
            .build();

    private _Any() {
    }
}
