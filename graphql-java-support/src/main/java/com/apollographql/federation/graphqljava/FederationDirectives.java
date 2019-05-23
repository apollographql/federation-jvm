package com.apollographql.federation.graphqljava;

import graphql.PublicApi;
import graphql.language.*;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.*;
import static graphql.language.DirectiveDefinition.newDirectiveDefinition;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.InputValueDefinition.newInputValueDefinition;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;

@PublicApi
public final class FederationDirectives {
    private static final GraphQLArgument fieldsArgument = newArgument()
            .name("fields")
            .type(new GraphQLNonNull(GraphQLString))
            .build();
    private static final InputValueDefinition fieldsDefinition = newInputValueDefinition()
            .name("fields")
            .type(new NonNullType(new TypeName(GraphQLString.getName())))
            .build();

    private static final DirectiveLocation DL_OBJECT = newDirectiveLocation().name("OBJECT").build();
    private static final DirectiveLocation DL_INTERFACE = newDirectiveLocation().name("INTERFACE").build();
    private static final DirectiveLocation DL_FIELD_DEFINITION = newDirectiveLocation().name("FIELD_DEFINITION").build();

    static final String keyName = "key";
    public static final GraphQLDirective key = newDirective()
            .name(keyName)
            .validLocations(OBJECT, INTERFACE)
            .argument(fieldsArgument)
            .build();
    public static final DirectiveDefinition keyDefinition = newDirectiveDefinition()
            .name(keyName)
            .directiveLocations(Arrays.asList(DL_OBJECT, DL_INTERFACE))
            .inputValueDefinition(fieldsDefinition)
            .build();

    static final String externalName = "external";
    public static final GraphQLDirective external = newDirective()
            .name(externalName)
            .validLocations(OBJECT, FIELD_DEFINITION)
            .build();
    public static final DirectiveDefinition externalDefinition = newDirectiveDefinition()
            .name(externalName)
            .directiveLocations(Arrays.asList(DL_OBJECT, DL_FIELD_DEFINITION))
            .build();

    static final String requiresName = "requires";
    public static final GraphQLDirective requires = newDirective()
            .name(requiresName)
            .validLocations(FIELD_DEFINITION)
            .argument(fieldsArgument)
            .build();
    public static final DirectiveDefinition requiresDefinition = newDirectiveDefinition()
            .name(requiresName)
            .directiveLocations(Arrays.asList(DL_FIELD_DEFINITION))
            .inputValueDefinition(fieldsDefinition)
            .build();

    static final String providesName = "provides";
    public static final GraphQLDirective provides = newDirective()
            .name(providesName)
            .validLocations(FIELD_DEFINITION)
            .argument(fieldsArgument)
            .build();
    public static final DirectiveDefinition providesDefinition = newDirectiveDefinition()
            .name(providesName)
            .directiveLocations(Arrays.asList(DL_FIELD_DEFINITION))
            .inputValueDefinition(fieldsDefinition)
            .build();

    static final String extendsName = "extends";
    public static final GraphQLDirective extends_ = newDirective()
            .name(extendsName)
            .validLocations(OBJECT)
            .build();
    public static final DirectiveDefinition extendsDefinition = newDirectiveDefinition()
            .name(extendsName)
            .directiveLocations(Arrays.asList(DL_OBJECT))
            .build();

    public static final Set<GraphQLDirective> allDirectives = new HashSet<>();
    public static final Set<SDLDefinition> allDefinitions = new HashSet<>();

    static {
        allDirectives.add(key);
        allDirectives.add(external);
        allDirectives.add(requires);
        allDirectives.add(provides);
        allDirectives.add(extends_);
        allDefinitions.add(keyDefinition);
        allDefinitions.add(externalDefinition);
        allDefinitions.add(requiresDefinition);
        allDefinitions.add(providesDefinition);
        allDefinitions.add(extendsDefinition);
    }

    private FederationDirectives() {
    }
}
