package com.apollographql.federation.graphqljava;

import graphql.Assert;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

import static graphql.schema.GraphQLScalarType.newScalar;

public final class _Any {
    public static final String typeName = "_Any";

    static final Coercing defaultCoercing = new Coercing() {
        @Override
        public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return dataFetcherResult;
        }

        @Override
        public Object parseValue(Object input) throws CoercingParseValueException {
            return input;
        }

        @Nullable
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
    };

    private _Any() {
    }

    static GraphQLScalarType type(Coercing coercing) {
        return newScalar()
                .name(typeName)
                .coercing(coercing)
                .build();
    }
}
