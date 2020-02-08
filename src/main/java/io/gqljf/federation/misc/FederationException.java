package io.gqljf.federation.misc;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

public final class FederationException extends GraphQLException implements GraphQLError {

    public FederationException(String message) {
        super(message);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return Collections.singletonList(new SourceLocation(-1, -1));
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.ValidationError;
    }
}
