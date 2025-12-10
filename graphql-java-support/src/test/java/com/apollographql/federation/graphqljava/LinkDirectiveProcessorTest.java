package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LinkDirectiveProcessorTest {
  // includes fed versions only for testing
  private static final Map<String, Integer> VERSIONS =
      Map.of(
          "v2.1", 21,
          "v2.3", 23,
          "v2.5", 25,
          "v2.6", 26,
          "v2.8", 28,
          "v2.9", 29,
          "v2.10", 210,
          "v2.11", 211,
          "v2.12", 212,
          "v10.10", 1010 // non-existent, just for testing purposes
          );

  private static final Pattern LINK_FED_VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+)");

  private static int parseFederationVersion(String specLink) {
    final String versionString = specLink.substring(specLink.length() - 3);
    try {
      return Math.round(Float.parseFloat(versionString) * 10);
    } catch (Exception e) {
      throw new UnsupportedFederationVersionException(specLink);
    }
  }

  public static int parseFederationVersionV2(String link) {
    final Matcher matcher = LINK_FED_VERSION_PATTERN.matcher(link);
    if (!matcher.find() || matcher.groupCount() != 2) {
      throw new UnsupportedFederationVersionException(link);
    }

    // minor version first
    try {
      double major = Integer.parseInt(matcher.group(1));
      final String minorStr = matcher.group(2);

      // major * 10^(digits in minor) + minor
      return (int) (major * Math.pow(10, minorStr.length()) + Integer.parseInt(minorStr));
    } catch (NumberFormatException e) {
      throw new UnsupportedFederationVersionException(link);
    }
  }

  @Test
  public void verifyParseFederationVersion23() {
    Assertions.assertEquals(
        VERSIONS.get("v2.3"), parseFederationVersionV2("https://specs.apollo.dev/federation/v2.3"));
  }

  @Test
  public void verifyParseFederationVersion212() {
    Assertions.assertEquals(
        VERSIONS.get("v2.12"),
        parseFederationVersionV2("https://specs.apollo.dev/federation/v2.12"));
  }

  @Test
  public void verifyParseFederationVersion1010() {
    Assertions.assertEquals(
        VERSIONS.get("v10.10"),
        parseFederationVersionV2("https://specs.apollo.dev/federation/v10.10"));
  }

  @Test
  void verifyAllFederationVersionsParse() {
    // just verify that all versions from 2.0 to 2.12 parse correctly
    for (int minor = 0; minor <= 12; minor++) {
      String version = String.format("v2.%d", minor);
      if (!VERSIONS.containsKey(version)) continue;

      String link = String.format("https://specs.apollo.dev/federation/v2.%d", minor);
      Assertions.assertEquals(VERSIONS.get(version), parseFederationVersionV2(link));
    }
  }
}
