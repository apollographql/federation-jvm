package com.apollographql.federation.graphqljava;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

public class FederationError extends GraphQLException implements GraphQLError {
    private static final List<SourceLocation> NO_WHERE =
            Collections.singletonList(new SourceLocation(-1, -1));

    FederationError(String message) {
        super(message);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return NO_WHERE;
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.ValidationError;
    }
}
