package com.apollographql.federation.graphqljava;

import static com.apollographql.federation.graphqljava.Federation.FEDERATION_SPEC_V2_0;
import static com.apollographql.federation.graphqljava.Federation.FEDERATION_SPEC_V2_1;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.language.DirectiveDefinition.newDirectiveDefinition;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.InputValueDefinition.newInputValueDefinition;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;

import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import graphql.PublicApi;
import graphql.language.*;
import graphql.parser.Parser;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PublicApi
public final class FederationDirectives {
  /* Directive locations */

  private static final DirectiveLocation DL_OBJECT = newDirectiveLocation().name("OBJECT").build();

  private static final DirectiveLocation DL_INTERFACE =
      newDirectiveLocation().name("INTERFACE").build();

  private static final DirectiveLocation DL_FIELD_DEFINITION =
      newDirectiveLocation().name("FIELD_DEFINITION").build();

  /* fields: _FieldSet */

  private static final GraphQLArgument fieldsArgument =
      newArgument().name("fields").type(new GraphQLNonNull(_FieldSet.type)).build();

  private static GraphQLArgument fieldsArgument(String value) {
    return newArgument(fieldsArgument).value(value).build();
  }

  private static final InputValueDefinition fieldsDefinition =
      newInputValueDefinition()
          .name("fields")
          .type(new NonNullType(new TypeName(_FieldSet.typeName)))
          .build();

  /* directive @key(fields: _FieldSet!) repeatable on OBJECT | INTERFACE */

  public static final String keyName = "key";

  public static final GraphQLDirective key =
      newDirective()
          .name(keyName)
          .validLocations(OBJECT, INTERFACE)
          .argument(fieldsArgument)
          .repeatable(true)
          .build();

  public static GraphQLDirective key(String fields) {
    return newDirective(key).argument(fieldsArgument(fields)).build();
  }

  public static final DirectiveDefinition keyDefinitionFed1 =
      newDirectiveDefinition()
          .name(keyName)
          .directiveLocations(Arrays.asList(DL_OBJECT, DL_INTERFACE))
          .inputValueDefinition(fieldsDefinition)
          .repeatable(true)
          .build();

  /* directive @external on FIELD_DEFINITION */

  public static final String externalName = "external";

  public static final GraphQLDirective external =
      newDirective().name(externalName).validLocations(FIELD_DEFINITION).build();

  public static final DirectiveDefinition externalDefinition =
      newDirectiveDefinition()
          .name(externalName)
          .directiveLocations(Collections.singletonList(DL_FIELD_DEFINITION))
          .build();

  /* directive @requires(fields: _FieldSet!) on FIELD_DEFINITION */

  public static final String requiresName = "requires";

  public static final GraphQLDirective requires =
      newDirective()
          .name(requiresName)
          .validLocations(FIELD_DEFINITION)
          .argument(fieldsArgument)
          .build();

  public static GraphQLDirective requires(String fields) {
    return newDirective(requires).argument(fieldsArgument(fields)).build();
  }

  public static final DirectiveDefinition requiresDefinition =
      newDirectiveDefinition()
          .name(requiresName)
          .directiveLocations(Collections.singletonList(DL_FIELD_DEFINITION))
          .inputValueDefinition(fieldsDefinition)
          .build();

  /* directive @provides(fields: _FieldSet!) on FIELD_DEFINITION */

  public static final String providesName = "provides";

  public static final GraphQLDirective provides =
      newDirective()
          .name(providesName)
          .validLocations(FIELD_DEFINITION)
          .argument(fieldsArgument)
          .build();

  public static GraphQLDirective provides(String fields) {
    return newDirective(provides).argument(fieldsArgument(fields)).build();
  }

  public static final DirectiveDefinition providesDefinition =
      newDirectiveDefinition()
          .name(providesName)
          .directiveLocations(Collections.singletonList(DL_FIELD_DEFINITION))
          .inputValueDefinition(fieldsDefinition)
          .build();

  /* directive @extends on OBJECT */

  public static final String extendsName = "extends";

  public static final GraphQLDirective extends_ =
      newDirective().name(extendsName).validLocations(OBJECT, INTERFACE).build();

  public static final DirectiveDefinition extendsDefinition =
      newDirectiveDefinition()
          .name(extendsName)
          .directiveLocations(Arrays.asList(DL_OBJECT, DL_INTERFACE))
          .build();

  private FederationDirectives() {}

  /* Sets */

  public static final Set<String> allNames;
  public static final Set<GraphQLDirective> allDirectives;
  public static final Set<DirectiveDefinition> allDefinitions;

  // use #loadFederationSpecDefinitions(string) instead
  @Deprecated
  static final List<SDLNamedDefinition> federation2Definitions =
      loadFederationSpecDefinitions(FEDERATION_SPEC_V2_0);

  public static final Set<DirectiveDefinition> federation1DirectiveDefinitions;

  static {
    // We need to maintain sorted order here for tests, since SchemaPrinter doesn't sort
    // directive definitions.
    allDirectives =
        Stream.of(key, external, requires, provides, extends_)
            .sorted(Comparator.comparing(GraphQLDirective::getName))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    allDefinitions =
        Stream.of(
                keyDefinitionFed1,
                externalDefinition,
                requiresDefinition,
                providesDefinition,
                extendsDefinition)
            .sorted(Comparator.comparing(DirectiveDefinition::getName))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    allNames =
        allDefinitions.stream()
            .map(DirectiveDefinition::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    federation1DirectiveDefinitions =
        Stream.of(
                keyDefinitionFed1,
                externalDefinition,
                requiresDefinition,
                providesDefinition,
                extendsDefinition)
            .sorted(Comparator.comparing(DirectiveDefinition::getName))
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static List<SDLNamedDefinition> loadFederationSpecDefinitions(String federationSpec) {
    switch (federationSpec) {
      case FEDERATION_SPEC_V2_0:
        return loadFed2Definitions("definitions_fed2_0.graphqls");
      case FEDERATION_SPEC_V2_1:
        return loadFed2Definitions("definitions_fed2_1.graphqls");
      default:
        throw new UnsupportedFederationVersionException(federationSpec);
    }
  }

  private static List<SDLNamedDefinition> loadFed2Definitions(String fileName) {
    InputStream inputStream =
        FederationDirectives.class.getClassLoader().getResourceAsStream(fileName);
    if (inputStream != null) {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        Document document = new Parser().parseDocument(reader);

        return document.getDefinitionsOfType(SDLNamedDefinition.class).stream()
            .sorted(Comparator.comparing(SDLNamedDefinition::getName))
            .collect(Collectors.toList());
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to load federation directive definitions from " + fileName);
      }
    } else {
      throw new RuntimeException(
          "Unable to load federation directive definitions from " + fileName);
    }
  }
}
