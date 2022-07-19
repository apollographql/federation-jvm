package com.apollographql.federation.graphqljava.printer;

import static graphql.util.TraversalControl.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.apollographql.federation.graphqljava.FederationDirectives;
import com.apollographql.federation.graphqljava.FileUtils;
import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.junit.jupiter.api.Test;

class ServiceSDLPrinterTest {
  final GraphQLObjectType PRODUCT_VARIATION_TYPE = GraphQLObjectType.newObject()
    .name("ProductVariation")
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("id")
        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
        .build()
    )
    .build();

  final GraphQLObjectType PRODUCT_DIMENSION_TYPE = GraphQLObjectType.newObject()
    .name("ProductDimension")
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("size")
        .type(Scalars.GraphQLString)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("weight")
        .type(Scalars.GraphQLFloat)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("unit")
        .type(Scalars.GraphQLString)
        .build()
    )
    .build();

  final GraphQLObjectType USER_TYPE = GraphQLObjectType.newObject()
    .name("User")
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("email")
        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("name")
        .type(Scalars.GraphQLString)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("totalProductsCreated")
        .type(Scalars.GraphQLInt)
        .build()
    )
    .build();

  private final GraphQLObjectType PRODUCT_TYPE = GraphQLObjectType.newObject()
    .name("Product")
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("id")
        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("sku")
        .type(Scalars.GraphQLString)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("package")
        .type(Scalars.GraphQLString)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("variation")
        .type(PRODUCT_VARIATION_TYPE)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("dimensions")
        .type(PRODUCT_DIMENSION_TYPE)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("createdBy")
        .type(USER_TYPE)
        .build()
    )
    .field(
      GraphQLFieldDefinition.newFieldDefinition()
        .name("notes")
        .type(Scalars.GraphQLString)
        .build()
    )
    .build();

  /*
  type Product @key(fields: "id") @key(fields: "sku package") @key(fields: "sku variation { id }") {
    id: ID!
    sku: String
    package: String
    variation: ProductVariation
    dimensions: ProductDimension
    createdBy: User @provides(fields: "totalProductsCreated")
    notes: String
  }

  type ProductVariation {
    id: ID!
  }

  type ProductDimension {
    size: String
    weight: Float
    unit: String
  }

  extend type Query {
    product(id: ID!): Product
  }

  type User @key(fields: "email") @extends {
    email: ID! @external
    name: String
    totalProductsCreated: Int @external
  }
   */
  private final GraphQLSchema TEST_SCHEMA =
    GraphQLSchema.newSchema()
      .query(
        GraphQLObjectType.newObject()
          .name("Query")
          .field(
            GraphQLFieldDefinition.newFieldDefinition()
              .name("product")
              .type(PRODUCT_TYPE)
              .argument(
                GraphQLArgument.newArgument()
                  .name("id")
                  .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
                  .build()
              )
              .build()))
      .additionalDirectives(FederationDirectives.allDirectives)
      .build();

  @Test
  public void generateServiceSDL_withDirectives() {
    final GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
        if (objectType.getName().equals("Product")) {
          final GraphQLObjectType productWithDirectives = objectType.transform(productBuilder -> productBuilder.withDirective(FederationDirectives.key("id"))
            .withDirective(FederationDirectives.key("sku package"))
            .withDirective(FederationDirectives.key("sku variation { id }")));
          return changeNode(context, productWithDirectives);
        }

        if (objectType.getName().equals("User")) {
          final GraphQLObjectType userWithDirectives = objectType.transform(userBuilder -> userBuilder.withDirective(FederationDirectives.key("email"))
            .withDirective(FederationDirectives.extends_));
          return changeNode(context, userWithDirectives);
        }
        return CONTINUE;
      }

      @Override
      public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        if (context.getParentNode() instanceof GraphQLObjectType) {
          final GraphQLObjectType parent = (GraphQLObjectType)context.getParentNode();
          if (parent.getName().equals("Product") && node.getName().equals("createdBy")) {
            final GraphQLFieldDefinition providesField = node.transform(fieldBuilder -> fieldBuilder.withDirective(FederationDirectives.provides("totalProductsCreated")));
            return changeNode(context, providesField);
          }

          if (parent.getName().equals("User") && (node.getName().equals("email") || node.getName().equals("totalProductsCreated"))) {
            final GraphQLFieldDefinition fieldWithDirectives = node.transform(fieldBuilder -> fieldBuilder.withDirective(FederationDirectives.external));
            return changeNode(context, fieldWithDirectives);
          }
        }
        return CONTINUE;
      }
    };
    GraphQLSchema schemaWithAppliedDirectives = graphql.schema.SchemaTransformer.transformSchema(TEST_SCHEMA, visitor);

    final String generatedSDL = ServiceSDLPrinter.generateServiceSDL(schemaWithAppliedDirectives, false);
    final String expectedSDL = FileUtils.readResource("schemas/federationV1_serviceSDL.graphql");
    assertEquals(expectedSDL, generatedSDL, "Generated service SDL is the same as expected one");
  }

  @Test
  public void generateServiceSDL_withAppliedDirectives() {
    final GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
        if (objectType.getName().equals("Product")) {
          final GraphQLObjectType productWithDirectives = objectType.transform(productBuilder -> productBuilder.withAppliedDirective(FederationDirectives.key("id").toAppliedDirective())
            .withAppliedDirective(FederationDirectives.key("sku package").toAppliedDirective())
            .withAppliedDirective(FederationDirectives.key("sku variation { id }").toAppliedDirective()));
          return changeNode(context, productWithDirectives);
        }

        if (objectType.getName().equals("User")) {
          final GraphQLObjectType userWithDirectives = objectType.transform(userBuilder -> userBuilder.withAppliedDirective(FederationDirectives.key("email").toAppliedDirective())
            .withAppliedDirective(FederationDirectives.extends_.toAppliedDirective()));
          return changeNode(context, userWithDirectives);
        }
        return CONTINUE;
      }

      @Override
      public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        if (context.getParentNode() instanceof GraphQLObjectType) {
          final GraphQLObjectType parent = (GraphQLObjectType)context.getParentNode();
          if (parent.getName().equals("Product") && node.getName().equals("createdBy")) {
            final GraphQLFieldDefinition providesField = node.transform(fieldBuilder -> fieldBuilder.withAppliedDirective(FederationDirectives.provides("totalProductsCreated").toAppliedDirective()));
            return changeNode(context, providesField);
          }

          if (parent.getName().equals("User") && (node.getName().equals("email") || node.getName().equals("totalProductsCreated"))) {
            final GraphQLFieldDefinition fieldWithDirectives = node.transform(fieldBuilder -> fieldBuilder.withAppliedDirective(FederationDirectives.external.toAppliedDirective()));
            return changeNode(context, fieldWithDirectives);
          }
        }
        return CONTINUE;
      }
    };
    GraphQLSchema schemaWithAppliedDirectives = graphql.schema.SchemaTransformer.transformSchema(TEST_SCHEMA, visitor);

    final String generatedSDL = ServiceSDLPrinter.generateServiceSDL(schemaWithAppliedDirectives, false);
    final String expectedSDL = FileUtils.readResource("schemas/federationV1_serviceSDL.graphql");
    assertEquals(expectedSDL, generatedSDL, "Generated service SDL is the same as expected one");
  }
}
