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
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.visibility.GraphqlFieldVisibility;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * graphql.schema.idl.SchemaPrinter wrapper that is used to generate SDL returned by the <code>
 * _service { sdl }</code> query and is compatible with Federation v1 and v2 specs.
 */
public final class ServiceSDLPrinter {

  private ServiceSDLPrinter() {
    // hidden constructor as this is static utility class
  }

  /**
   * Generate service SDL compatible with Federation v1 specification.
   *
   * @param schema target schema
   * @param queryTypeShouldBeEmpty boolean indicating whether query type contains "fake" query that
   *     should be removed (at least a single query has to be present for graphql-java to consider
   *     it as a valid schema)
   * @return SDL compatible with Federation v1
   */
  public static String generateServiceSDL(GraphQLSchema schema, boolean queryTypeShouldBeEmpty) {
    // Gather directive definitions to hide.
    final Set<String> hiddenDirectiveDefinitions = new HashSet<>();
    // Apollo Gateway will fail Federation v1 composition if it sees standard directive definitions.
    hiddenDirectiveDefinitions.addAll(
        DirectiveInfo.GRAPHQL_SPECIFICATION_DIRECTIVES.stream()
            .map(GraphQLDirective::getName)
            .collect(Collectors.toList()));
    hiddenDirectiveDefinitions.addAll(FederationDirectives.allNames);

    // Gather type definitions to hide.
    final Set<String> hiddenTypeDefinitions = new HashSet<>();
    hiddenTypeDefinitions.add(_Any.typeName);
    hiddenTypeDefinitions.add(_Entity.typeName);
    hiddenTypeDefinitions.add(_FieldSet.typeName);
    hiddenTypeDefinitions.add(_Service.typeName);

    final String queryTypeName = schema.getQueryType().getName();
    final GraphqlFieldVisibility oldFieldVisibility = schema.getCodeRegistry().getFieldVisibility();
    final GraphqlFieldVisibility newFieldVisibility =
        new GraphqlFieldVisibility() {
          @Override
          public List<GraphQLFieldDefinition> getFieldDefinitions(
              GraphQLFieldsContainer fieldsContainer) {
            if (fieldsContainer.getName().equals(queryTypeName)) {
              if (queryTypeShouldBeEmpty) {
                return Collections.emptyList();
              } else {
                return fieldsContainer.getFieldDefinitions().stream()
                    .filter(
                        (field) ->
                            !_Service.fieldName.equals(field.getName())
                                && !_Entity.fieldName.equals(field.getName()))
                    .collect(Collectors.toList());
              }
            } else {
              return oldFieldVisibility.getFieldDefinitions(fieldsContainer);
            }
          }

          @Override
          public GraphQLFieldDefinition getFieldDefinition(
              GraphQLFieldsContainer fieldsContainer, String fieldName) {
            if (fieldsContainer.getName().equals(queryTypeName)
                && (queryTypeShouldBeEmpty
                    || _Service.fieldName.equals(fieldName)
                    || _Entity.fieldName.equals(fieldName))) {
              return null;
            } else {
              return oldFieldVisibility.getFieldDefinition(fieldsContainer, fieldName);
            }
          }
        };
    final GraphQLCodeRegistry newCodeRegistry =
        schema
            .getCodeRegistry()
            .transform(
                codeRegistryBuilder -> codeRegistryBuilder.fieldVisibility(newFieldVisibility));
    final GraphQLSchema federatedSchema =
        schema.transform(schemaBuilder -> schemaBuilder.codeRegistry(newCodeRegistry));

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
    return new SchemaPrinter(options).print(federatedSchema).trim();
  }

  /**
   * Generate service SDL compatible with Federation v2 specification.
   *
   * @param schema target schema
   * @return SDL compatible with Federation v2
   */
  public static String generateServiceSDLV2(GraphQLSchema schema) {
    // federation v2 SDL does not need to filter federation directive definitions
    return new SchemaPrinter(
            SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
                .includeScalarTypes(true)
                .includeDirectives(
                    directive -> !DirectiveInfo.isGraphqlSpecifiedDirective(directive)))
        .print(schema)
        .trim();
  }
}
