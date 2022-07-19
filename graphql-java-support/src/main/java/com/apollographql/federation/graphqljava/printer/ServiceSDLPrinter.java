package com.apollographql.federation.graphqljava.printer;

import com.apollographql.federation.graphqljava.FederationDirectives;
import com.apollographql.federation.graphqljava._Any;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.federation.graphqljava._FieldSet;
import com.apollographql.federation.graphqljava._Service;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.visibility.GraphqlFieldVisibility;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ServiceSDLPrinter {

  // Apollo Gateway will fail Federation v1 composition if it sees standard directive definitions.
  private static final Set<String> STANDARD_DIRECTIVES =
    new HashSet<>(Arrays.asList("deprecated", "include", "skip", "specifiedBy"));

  private ServiceSDLPrinter() {
    // hidden constructor as this is static utility class
  }

  public static String generateServiceSDL(GraphQLSchema schema, boolean queryTypeShouldBeEmpty) {
    // Gather directive definitions to hide.
    final Set<String> hiddenDirectiveDefinitions = new HashSet<>();
    hiddenDirectiveDefinitions.addAll(STANDARD_DIRECTIVES);
    hiddenDirectiveDefinitions.addAll(FederationDirectives.allNames);

    // Gather type definitions to hide.
    final Set<String> hiddenTypeDefinitions = new HashSet<>();
    hiddenTypeDefinitions.add(_Any.typeName);
    hiddenTypeDefinitions.add(_Entity.typeName);
    hiddenTypeDefinitions.add(_FieldSet.typeName);
    hiddenTypeDefinitions.add(_Service.typeName);

    // Change field visibility for the query type if needed.
    if (queryTypeShouldBeEmpty) {
      final String queryTypeName = schema.getQueryType().getName();
      final GraphqlFieldVisibility oldFieldVisibility =
        schema.getCodeRegistry().getFieldVisibility();
      final GraphqlFieldVisibility newFieldVisibility =
        new GraphqlFieldVisibility() {
          @Override
          public List<GraphQLFieldDefinition> getFieldDefinitions(
            GraphQLFieldsContainer fieldsContainer) {
            return fieldsContainer.getName().equals(queryTypeName)
              ? Collections.emptyList()
              : oldFieldVisibility.getFieldDefinitions(fieldsContainer);
          }

          @Override
          public GraphQLFieldDefinition getFieldDefinition(
            GraphQLFieldsContainer fieldsContainer, String fieldName) {
            return fieldsContainer.getName().equals(queryTypeName)
              ? null
              : oldFieldVisibility.getFieldDefinition(fieldsContainer, fieldName);
          }
        };
      final GraphQLCodeRegistry newCodeRegistry =
        schema
          .getCodeRegistry()
          .transform(
            codeRegistryBuilder -> codeRegistryBuilder.fieldVisibility(newFieldVisibility));
      schema = schema.transform(schemaBuilder -> schemaBuilder.codeRegistry(newCodeRegistry));
    }

    final Predicate<GraphQLSchemaElement> excludeFedTypeDefinitions =
      element ->
        !(element instanceof GraphQLNamedSchemaElement
          && hiddenTypeDefinitions.contains(((GraphQLNamedSchemaElement) element).getName()));
    final Predicate<GraphQLSchemaElement> excludeFedDirectiveDefinitions =
      element ->
        !(element instanceof GraphQLDirective
          && hiddenDirectiveDefinitions.contains(((GraphQLDirective) element).getName()));
    final SchemaPrinter.Options options =
      SchemaPrinter.Options.defaultOptions()
        .includeScalarTypes(true)
        .includeSchemaDefinition(true)
        .includeDirectives(FederationDirectives.allNames::contains)
        .includeSchemaElement(
          element ->
            excludeFedTypeDefinitions.test(element)
              && excludeFedDirectiveDefinitions.test(element));
    return new SchemaPrinter(options).print(schema).trim();
  }

  public static String generateServiceSDLV2(GraphQLSchema schema) {
    // federation v2 SDL does not need to filter federation directive definitions
    final Set<String> standardDirectives =
      new HashSet<>(Arrays.asList("deprecated", "include", "skip", "specifiedBy"));
    return new SchemaPrinter(
        SchemaPrinter.Options.defaultOptions()
          .includeSchemaDefinition(true)
          .includeScalarTypes(true)
          .includeDirectives(def -> !standardDirectives.contains(def))
      )
      .print(schema)
      .trim();
  }
}
