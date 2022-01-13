package com.apollographql.federation.graphqljava.caching;

import com.apollographql.federation.graphqljava._Entity;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.schema.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * A GraphQL Java Instrumentation that computes a max age for an operation based on @cacheControl
 * directives.
 *
 * <p>You can retrieve the "max-age=..." header value with a {@link graphql.GraphQLContext}: <code>
 * String cacheControlHeader = CacheControlInstrumentation.cacheControlContext(context);
 * </code>
 *
 * <p>See https://www.apollographql.com/docs/apollo-server/performance/caching/ and the original
 * implementation at
 * https://github.com/apollographql/apollo-server/blob/main/packages/apollo-server-core/src/plugin/cacheControl/index.ts
 */
public class CacheControlInstrumentation extends SimpleInstrumentation {
  private final int defaultMaxAge;

  private static final Object CONTEXT_KEY = new Object();
  private static final String DIRECTIVE_NAME = "cacheControl";
  private static final String MAX_AGE = "maxAge";
  private static final String SCOPE = "scope";
  private static final String INHERIT_MAX_AGE = "inheritMaxAge";

  public CacheControlInstrumentation() {
    this(0);
  }

  public CacheControlInstrumentation(int defaultMaxAge) {
    this.defaultMaxAge = defaultMaxAge;
  }

  @Nullable
  public static String cacheControlHeaderFromGraphQLContext(GraphQLContext context) {
    return context.get(CONTEXT_KEY);
  }

  @Override
  public InstrumentationState createState() {
    return new CacheControlState();
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(
      InstrumentationExecutionParameters parameters) {
    return new InstrumentationContext<ExecutionResult>() {
      @Override
      public void onDispatched(CompletableFuture<ExecutionResult> completableFuture) {}

      @Override
      public void onCompleted(ExecutionResult executionResult, Throwable throwable) {
        CacheControlState state = parameters.getInstrumentationState();

        // Attach the policy to the context object
        state
            .overallPolicy
            .maybeAsString()
            .ifPresent(s -> parameters.getGraphQLContext().put(CONTEXT_KEY, s));
      }
    };
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginField(
      InstrumentationFieldParameters parameters) {
    CacheControlState state = parameters.getInstrumentationState();
    CacheControlPolicy fieldPolicy = new CacheControlPolicy();
    boolean inheritMaxAge = false;

    GraphQLUnmodifiedType unwrappedFieldType =
        GraphQLTypeUtil.unwrapAll(parameters.getExecutionStepInfo().getType());

    // There's no way to set a cacheControl directive on the _entities field or
    // the _Entity union in SDL. Instead, we can determine the possible concrete
    // types from the representations arguments and select the most restrictive
    // cache policy from those types.

    if (unwrappedFieldType.getName().equals(_Entity.typeName)) {
      Object representations = parameters.getExecutionStepInfo().getArgument(_Entity.argumentName);

      if (representations instanceof List) {
        typesFromEntitiesArgument(
                representations, parameters.getExecutionContext().getGraphQLSchema())
            .stream()
            .map(
                type ->
                    CacheControlDirective.fromDirectiveContainer((GraphQLDirectiveContainer) type))
            .filter(Optional::isPresent)
            .forEach(directive -> fieldPolicy.restrict(directive.get()));
      }
    } else if (unwrappedFieldType instanceof GraphQLCompositeType
        && unwrappedFieldType instanceof GraphQLDirectiveContainer) {

      // Cache directive on the return type of this field if it's a composite type

      Optional<CacheControlDirective> directive =
          CacheControlDirective.fromDirectiveContainer(
              (GraphQLDirectiveContainer) unwrappedFieldType);

      if (directive.isPresent()) {
        fieldPolicy.replace(directive.get());
        inheritMaxAge = directive.get().getInheritMaxAge();
      }
    }

    // Cache directive on the field itself

    Optional<CacheControlDirective> fieldDirective =
        CacheControlDirective.fromDirectiveContainer(parameters.getField());
    if (fieldDirective.isPresent()) {
      CacheControlDirective directive = fieldDirective.get();

      // If inheritMaxAge is true, take note of that to avoid setting the
      // default max age in the next step. This does allow setting the cache
      // scope though.
      //
      // Note that specifying `@cacheControl(inheritMaxAge: true)` on a
      // field whose return type defines a `maxAge` gives precedence to
      // the type's `maxAge`. (Perhaps this should be some sort of
      // error.)
      if (directive.getInheritMaxAge() && !fieldPolicy.hasMaxAge()) {
        inheritMaxAge = true;
        fieldPolicy.replace(directive.getScope());
      } else {
        fieldPolicy.replace(directive);
      }
    }

    // If this field returns a composite type or is a root field and
    // we haven't seen an explicit maxAge argument, set the maxAge to 0
    // (uncached) or the default if specified in the constructor.
    // (Non-object fields by default are assumed to inherit their
    // cacheability from their parents. But on the other hand, while
    // root non-object fields can get explicit directives from their
    // definition on the Query/Mutation object, if that doesn't exist
    // then there's no parent field that would assign the default
    // maxAge, so we do it here.)
    //
    // You can disable this on a non-root field by writing
    // `@cacheControl(inheritMaxAge: true)` on it. If you do this,
    // then its children will be treated like root paths, since there
    // is no parent maxAge to inherit.

    if (!fieldPolicy.hasMaxAge()
        && ((unwrappedFieldType instanceof GraphQLCompositeType && !inheritMaxAge)
            || parameters.getExecutionStepInfo().getPath().isRootPath())) {
      fieldPolicy.restrict(defaultMaxAge);
    }

    state.overallPolicy.restrict(fieldPolicy);

    return super.beginField(parameters);
  }

  enum CacheControlScope {
    PUBLIC,
    PRIVATE
  }

  private static class CacheControlState implements InstrumentationState {
    public final CacheControlPolicy overallPolicy = new CacheControlPolicy();
  }

  private static class CacheControlPolicy {
    @Nullable private Integer maxAge;
    @Nullable private CacheControlScope scope = CacheControlScope.PUBLIC;

    void restrict(CacheControlPolicy policy) {
      if (policy.maxAge != null && (maxAge == null || policy.maxAge < maxAge)) {
        this.maxAge = policy.maxAge;
      }

      if (policy.scope != null && (scope == null || !scope.equals(CacheControlScope.PRIVATE))) {
        this.scope = policy.scope;
      }
    }

    void restrict(CacheControlDirective directive) {
      if (directive.maxAge != null && (maxAge == null || directive.maxAge < maxAge)) {
        this.maxAge = directive.maxAge;
      }

      if (directive.scope != null && (scope == null || !scope.equals(CacheControlScope.PRIVATE))) {
        this.scope = directive.scope;
      }
    }

    void restrict(Integer maxAge) {
      if (this.maxAge == null || maxAge < this.maxAge) {
        this.maxAge = maxAge;
      }
    }

    void replace(CacheControlDirective directive) {
      if (directive.maxAge != null) {
        this.maxAge = directive.maxAge;
      }

      if (directive.scope != null) {
        this.scope = directive.scope;
      }
    }

    void replace(@Nullable CacheControlScope scope) {
      if (scope != null) {
        this.scope = scope;
      }
    }

    public Optional<String> maybeAsString() {
      Integer maxAgeValue = maxAge == null ? 0 : maxAge;
      if (maxAgeValue.equals(0)) {
        return Optional.empty();
      }

      CacheControlScope scopeValue = scope == null ? CacheControlScope.PUBLIC : scope;
      return Optional.of(
          String.format("max-age=%d, %s", maxAgeValue, scopeValue.toString().toLowerCase()));
    }

    public boolean hasMaxAge() {
      return maxAge != null;
    }

    public boolean hasScope() {
      return scope != null;
    }
  }

  private static class CacheControlDirective {
    @Nullable private final Integer maxAge;
    @Nullable private final CacheControlScope scope;
    @Nullable private final Boolean inheritMaxAge;

    public static Optional<CacheControlDirective> fromDirectiveContainer(
        GraphQLDirectiveContainer container) {
      GraphQLDirective directive = container.getDirective(DIRECTIVE_NAME);

      if (directive == null) {
        return Optional.empty();
      }

      Integer maxAge =
          Optional.ofNullable(directive.getArgument(MAX_AGE))
              .map(a -> GraphQLArgument.getArgumentValue(a))
              .filter(v -> v instanceof Integer)
              .map(Integer.class::cast)
              .orElse(null);

      CacheControlScope scope =
          Optional.ofNullable(directive.getArgument(SCOPE))
              .map(a -> GraphQLArgument.getArgumentValue(a))
              .filter(v -> v instanceof String)
              .map(s -> CacheControlScope.valueOf((String) s))
              .orElse(null);

      Boolean inheritMaxAge =
          Optional.ofNullable(directive.getArgument(INHERIT_MAX_AGE))
              .map(a -> GraphQLArgument.getArgumentValue(a))
              .filter(v -> v instanceof Boolean)
              .map(Boolean.class::cast)
              .orElse(null);

      return Optional.of(new CacheControlDirective(maxAge, scope, inheritMaxAge));
    }

    public CacheControlDirective(
        @Nullable Integer maxAge,
        @Nullable CacheControlScope scope,
        @Nullable Boolean inheritMaxAge) {
      this.maxAge = maxAge;
      this.scope = scope;
      this.inheritMaxAge = inheritMaxAge;
    }

    public boolean isRestricted() {
      return maxAge != null || scope != null;
    }

    @Nullable
    public Integer getMaxAge() {
      return maxAge;
    }

    public boolean hasMaxAge() {
      return maxAge != null;
    }

    @Nullable
    public CacheControlScope getScope() {
      return scope;
    }

    public boolean hasScope() {
      return scope != null;
    }

    public Boolean getInheritMaxAge() {
      return inheritMaxAge != null && inheritMaxAge;
    }

    public boolean hasInheritMaxAge() {
      return inheritMaxAge != null;
    }

    public String toString() {
      return String.format(
          "@cacheControl(maxAge: %s, scope: %s, inheritMaxAge: %s)", maxAge, scope, inheritMaxAge);
    }
  }

  static List<GraphQLType> typesFromEntitiesArgument(Object representations, GraphQLSchema schema) {
    if (representations instanceof List) {
      return ((List<?>) representations)
          .stream()
              .filter(rep -> rep instanceof Map<?, ?>)
              .map(rep -> ((Map<?, ?>) rep).get("__typename"))
              .map(Object::toString)
              .distinct()
              .map(schema::getType)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}
