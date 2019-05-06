package com.apollographql.federation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

final class TestUtils {
    static String readResource(String name) {
        InputStream is = SchemaUtils.class.getResourceAsStream(name);
        assert is != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
    }

    private TestUtils() {
    }
}
