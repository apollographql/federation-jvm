package com.apollographql.federation.graphqljava;

import static graphql.introspection.Introspection.DirectiveLocation.*;
import static graphql.language.DirectiveDefinition.newDirectiveDefinition;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.InputValueDefinition.newInputValueDefinition;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;

import graphql.PublicApi;
import graphql.language.*;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
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

  private static final DirectiveLocation DL_SCHEMA = newDirectiveLocation().name("SCHEMA").build();

  private static final DirectiveLocation DL_UNION = newDirectiveLocation().name("UNION").build();

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

  public static final DirectiveDefinition keyDefinitionFed2 =
      newDirectiveDefinition()
          .name(keyName)
          .directiveLocations(Arrays.asList(DL_OBJECT, DL_INTERFACE))
          .inputValueDefinition(fieldsDefinition)
          .inputValueDefinition(
              newInputValueDefinition().name("resolvable").type(new TypeName("Boolean")).build())
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

  /* directive @shareable on FIELD_DEFINITION | OBJECT */

  public static final String shareableName = "shareable";

  public static final DirectiveDefinition shareableDefinition =
      newDirectiveDefinition()
          .name(shareableName)
          .directiveLocations(Arrays.asList(DL_FIELD_DEFINITION, DL_OBJECT))
          .build();

  /*
   * directive @link( """ Url of the linked core feature. """ url: String!, """ Optional list of
   * element names to import in the top-level namesapce. Elements of the feature not part of this
   * list can only be referenced by prefixing the element named by the feature name (for instance,
   * if the `@key` is not imported in this list for the `federation` feature, it can still be
   * refered to using `@federation__key`). """ import: [link__Import], ) repeatable on SCHEMA
   */
  public static final String linkName = "link";

  public static final DirectiveDefinition linkDefinition =
      newDirectiveDefinition()
          .name(linkName)
          .directiveLocations(Collections.singletonList(DL_SCHEMA))
          .inputValueDefinition(
              newInputValueDefinition()
                  .name("url")
                  .type(new NonNullType(new TypeName("String")))
                  .build())
          .inputValueDefinition(
              newInputValueDefinition()
                  .name("import")
                  .type(new ListType(new TypeName("link__Import")))
                  .build())
          .repeatable(true)
          .build();

  /* directive @tag(name: String!) repeatable on FIELD_DEFINITION | INTERFACE | OBJECT | UNION */
  public static final String tagName = "tag";

  public static final DirectiveDefinition tagDefinition =
      newDirectiveDefinition()
          .name(tagName)
          .inputValueDefinition(
              newInputValueDefinition()
                  .name("name")
                  .type(new NonNullType(new TypeName("String")))
                  .build())
          .directiveLocations(Arrays.asList(DL_FIELD_DEFINITION, DL_INTERFACE, DL_OBJECT, DL_UNION))
          .repeatable(true)
          .build();

  // directive @override(from: String!) on FIELD_DEFINITION
  public static final DirectiveDefinition overrideDefinition =
      newDirectiveDefinition()
          .name("override")
          .inputValueDefinition(
              newInputValueDefinition()
                  .name("from")
                  .type(new NonNullType(new TypeName("String")))
                  .build())
          .directiveLocations(Arrays.asList(DL_FIELD_DEFINITION))
          .repeatable(true)
          .build();

  private FederationDirectives() {}

  /* Sets */

  public static final Set<String> allNames;
  public static final Set<GraphQLDirective> allDirectives;
  public static final Set<DirectiveDefinition> allDefinitions;
  public static final Set<DirectiveDefinition> federation2DirectiveDefinitions;
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
    federation2DirectiveDefinitions =
        Stream.of(
                keyDefinitionFed2,
                externalDefinition,
                requiresDefinition,
                providesDefinition,
                extendsDefinition,
                shareableDefinition,
                linkDefinition,
                tagDefinition,
                overrideDefinition)
            .sorted(Comparator.comparing(DirectiveDefinition::getName))
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
