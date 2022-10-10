package com.apollographql.federation.graphqljava.directives;

import static graphql.util.TreeTransformerUtil.changeNode;

import com.apollographql.federation.graphqljava.exceptions.UnsupportedRenameException;
import graphql.language.DirectiveDefinition;
import graphql.language.NamedNode;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.ScalarTypeDefinition;
import graphql.language.TypeName;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * GraphQL schema node visitor that supports renaming imported elements specified in the <code>@link
 * </code> directive.
 */
class LinkImportsRenamingVisitor extends NodeVisitorStub {
  private static final Set<String> BUILT_IN_SCALARS =
      new HashSet<>(Arrays.asList("String", "Boolean", "Int", "Float", "ID"));

  private final Map<String, String> fed2Imports;

  public LinkImportsRenamingVisitor(Map<String, String> fed2Imports) {
    this.fed2Imports = fed2Imports;
  }

  @Override
  protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
    if (node instanceof NamedNode) {
      Node newNode = null;
      if (node instanceof TypeName) {
        String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, false);
        newNode = ((TypeName) node).transform(builder -> builder.name(newName));
      } else if (node instanceof ScalarTypeDefinition) {
        String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, false);
        newNode = ((ScalarTypeDefinition) node).transform(builder -> builder.name(newName));
      } else if (node instanceof DirectiveDefinition) {
        String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, true);
        newNode = ((DirectiveDefinition) node).transform(builder -> builder.name(newName));
      }
      if (newNode != null) {
        return changeNode(context, newNode);
      }
    }
    return super.visitNode(node, context);
  }

  private String newName(String name, Map<String, String> fed2Imports, boolean isDirective) {
    String key;
    if (isDirective) {
      key = "@" + name;
    } else {
      key = name;
    }

    if (BUILT_IN_SCALARS.contains(key)) {
      // Do not rename builtin types
      return name;
    }

    if (fed2Imports.containsKey(key)) {
      String newName = fed2Imports.get(key);
      if (("@tag".equals(key) || "@inaccessible".equals(key)) && !newName.equals(key)) {
        throw new UnsupportedRenameException(key);
      }

      if (isDirective) {
        return newName.substring(1);
      } else {
        return newName;
      }
    } else {
      if (name.equals("inaccessible") || name.equals("tag")) {
        return name;
      } else if (name.equals("Import")) {
        return "link__" + name;
      } else {
        // apply default namespace
        return "federation__" + name;
      }
    }
  }
}
