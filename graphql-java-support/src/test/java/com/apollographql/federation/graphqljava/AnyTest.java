package com.apollographql.federation.graphqljava;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AnyTest {

  @Test
  public void verifyAnyCoercingCanSerializeValue() {
    Coercing coercing = _Any.type.getCoercing();
    Assertions.assertEquals(1, coercing.serialize(1, GraphQLContext.getDefault(), Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseValue() {
    Coercing coercing = _Any.type.getCoercing();
    Assertions.assertEquals(1, coercing.parseValue(1, GraphQLContext.getDefault(), Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseLiteralNull() {
    Coercing coercing = _Any.type.getCoercing();

    Assertions.assertNull(
        coercing.parseLiteral(
            NullValue.of(),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseLiteralScalars() {
    Coercing coercing = _Any.type.getCoercing();

    Assertions.assertEquals(
        BigDecimal.valueOf(1.5),
        coercing.parseLiteral(
            FloatValue.of(1.5),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
    Assertions.assertEquals(
        "hello world!",
        coercing.parseLiteral(
            StringValue.of("hello world!"),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
    Assertions.assertEquals(
        BigInteger.valueOf(123),
        coercing.parseLiteral(
            IntValue.of(123),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
    Assertions.assertEquals(
        true,
        coercing.parseLiteral(
            BooleanValue.of(true),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseLiteralEnum() {
    Coercing coercing = _Any.type.getCoercing();

    Assertions.assertEquals(
        "MyEnum",
        coercing.parseLiteral(
            EnumValue.of("MyEnum"),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseLiteralListValues() {
    Coercing coercing = _Any.type.getCoercing();

    List<Value> values = new ArrayList<>();
    values.add(StringValue.of("one"));
    values.add(StringValue.of("two"));

    List<String> expected = new ArrayList<>();
    expected.add("one");
    expected.add("two");

    Assertions.assertEquals(
        expected,
        coercing.parseLiteral(
            new ArrayValue(values),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US));
  }

  @Test
  public void verifyAnyCoercingCanParseLiteralObjectValues() {
    Coercing coercing = _Any.type.getCoercing();

    List<ObjectField> fields = new ArrayList<>();
    fields.add(new ObjectField("intField", IntValue.of(123)));
    fields.add(new ObjectField("stringField", StringValue.of("foo")));

    Object objectResult =
        coercing.parseLiteral(
            new ObjectValue(fields),
            CoercedVariables.emptyVariables(),
            GraphQLContext.getDefault(),
            Locale.US);
    Assertions.assertTrue(objectResult instanceof Map);

    Map<String, Object> result = (Map<String, Object>) objectResult;
    Assertions.assertEquals(2, result.size());
    Assertions.assertEquals(BigInteger.valueOf(123), result.get("intField"));
    Assertions.assertEquals("foo", result.get("stringField"));
  }
}
