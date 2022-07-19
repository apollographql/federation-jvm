package com.apollographql.federation.graphqljava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class FileUtils {

  private static final String NEW_LINE_SEPARATOR = "\n";

  public static String readResource(String name) {
    try (InputStream is = FederatedSchemaVerifier.class.getClassLoader().getResourceAsStream(name)) {
      if (is == null) {
        throw new RuntimeException("Unable to locate the target file " + name);
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String contents = reader.lines().collect(Collectors.joining(NEW_LINE_SEPARATOR)).trim();
        assert contents.length() > 0;
        return contents;
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to read the contents of " + name + " file.");
    }
  }
}
