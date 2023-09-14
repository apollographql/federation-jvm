package com.apollographql.federation.graphqljava.directives;

import static com.apollographql.federation.graphqljava.FederationDirectives.loadFederationSpecDefinitions;

import com.apollographql.federation.graphqljava.exceptions.MultipleFederationLinksException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedLinkImportException;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.AstTransformer;
import graphql.language.Directive;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.SDLNamedDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public final class LinkDirectiveProcessor {

  private LinkDirectiveProcessor() {}

  /**
   * Load all Federation V2 definitions based on the `@link` imports.
   *
   * @param typeDefinitionRegistry
   * @return Stream of Federation V2 SDLNamedDefinitions
   * @throws MultipleFederationLinksException if schema contains multiple `@link` directives
   *     importing federation specification
   */
  public static @Nullable Stream<SDLNamedDefinition> loadFederationImportedDefinitions(
      TypeDefinitionRegistry typeDefinitionRegistry) {

    Stream<Directive> schemaLinkDirectives =
        typeDefinitionRegistry
            .schemaDefinition()
            .map(LinkDirectiveProcessor::getFederationLinkDirectives)
            .orElse(Stream.empty());

    Stream<Directive> extensionLinkDirectives =
        typeDefinitionRegistry.getSchemaExtensionDefinitions().stream()
            .flatMap(LinkDirectiveProcessor::getFederationLinkDirectives);

    List<Directive> federationLinkDirectives =
        Stream.concat(schemaLinkDirectives, extensionLinkDirectives).collect(Collectors.toList());

    if (federationLinkDirectives.isEmpty()) {
      return null;
    } else if (federationLinkDirectives.size() > 1) {
      throw new MultipleFederationLinksException(federationLinkDirectives);
    } else {
      return loadDefinitions(federationLinkDirectives.get(0));
    }
  }

  private static Stream<SDLNamedDefinition> loadDefinitions(Directive linkDirective) {
    final Map<String, String> imports = parseLinkImports(linkDirective);

    final Argument urlArgument = linkDirective.getArgument("url");
    final String specLink = ((StringValue) urlArgument.getValue()).getValue();

    final int federationVersion = parseFederationVersion(specLink);
    if (imports.containsKey("@composeDirective")
        && !isComposeDirectiveSupported(federationVersion)) {
      throw new UnsupportedLinkImportException("@composeDirective");
    }

    if (imports.containsKey("@interfaceObject") && !isInterfaceObjectSupported(federationVersion)) {
      throw new UnsupportedLinkImportException("@interfaceObject");
    }

    if (imports.containsKey("@authenticated") && !isAuthorizationSupported(federationVersion)) {
      throw new UnsupportedLinkImportException("@authenticated");
    }

    if (imports.containsKey("@requiresScopes") && !isAuthorizationSupported(federationVersion)) {
      throw new UnsupportedLinkImportException("@requiresScopes");
    }

    return loadFederationSpecDefinitions(specLink).stream()
        .map(
            definition ->
                (SDLNamedDefinition)
                    new AstTransformer()
                        .transform(definition, new LinkImportsRenamingVisitor(imports)));
  }

  private static int parseFederationVersion(String specLink) {
    final String versionString = specLink.substring(specLink.length() - 3);
    try {
      return Math.round(Float.parseFloat(versionString) * 10);
    } catch (Exception e) {
      throw new UnsupportedFederationVersionException(specLink);
    }
  }

  private static boolean isComposeDirectiveSupported(int federationVersion) {
    return federationVersion >= 21;
  }

  private static boolean isInterfaceObjectSupported(int federationVersion) {
    return federationVersion >= 23;
  }

  private static boolean isAuthorizationSupported(int federationVersion) {
    return federationVersion >= 25;
  }

  private static Stream<Directive> getFederationLinkDirectives(SchemaDefinition schemaDefinition) {
    return schemaDefinition.getDirectives("link").stream()
        .filter(
            directive -> {
              Argument urlArgument = directive.getArgument("url");
              if (urlArgument != null && urlArgument.getValue() instanceof StringValue) {
                StringValue value = (StringValue) urlArgument.getValue();
                return value.getValue().startsWith("https://specs.apollo.dev/federation/");
              } else {
                return false;
              }
            });
  }

  private static Map<String, String> parseLinkImports(Directive linkDirective) {
    final Map<String, String> imports = new HashMap<>();

    final Argument importArgument = linkDirective.getArgument("import");
    if (importArgument != null && importArgument.getValue() instanceof ArrayValue) {
      final ArrayValue linkImports = (ArrayValue) importArgument.getValue();
      for (Value importedDefinition : linkImports.getValues()) {
        if (importedDefinition instanceof StringValue) {
          // no rename
          final String name = ((StringValue) importedDefinition).getValue();
          imports.put(name, name);
        } else if (importedDefinition instanceof ObjectValue) {
          // renamed import
          final ObjectValue importedObjectValue = (ObjectValue) importedDefinition;

          final Optional<ObjectField> nameField =
              importedObjectValue.getObjectFields().stream()
                  .filter(field -> field.getName().equals("name"))
                  .findFirst();
          final Optional<ObjectField> renameAsField =
              importedObjectValue.getObjectFields().stream()
                  .filter(field -> field.getName().equals("as"))
                  .findFirst();

          if (!nameField.isPresent() || !(nameField.get().getValue() instanceof StringValue)) {
            throw new UnsupportedLinkImportException(importedObjectValue);
          }
          final String name = ((StringValue) nameField.get().getValue()).getValue();

          if (!renameAsField.isPresent()) {
            imports.put(name, name);
          } else {
            final Value renamedAsValue = renameAsField.get().getValue();
            if (!(renamedAsValue instanceof StringValue)) {
              throw new UnsupportedLinkImportException(importedObjectValue);
            }
            imports.put(name, ((StringValue) renamedAsValue).getValue());
          }
        } else {
          throw new UnsupportedLinkImportException(importedDefinition);
        }
      }
    }

    imports.put("@link", "@link");
    return imports;
  }
}
